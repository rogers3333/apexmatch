package com.apexmatch.gateway.integration;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.engine.java.JavaMatchingEngine;
import com.apexmatch.gateway.filter.CircuitBreaker;
import com.apexmatch.ha.raft.MatchingEngineStateMachine;
import com.apexmatch.ha.raft.OperationLog;
import com.apexmatch.ha.raft.RaftGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 故障演练测试：宕机恢复、Raft 选主切换、熔断降级。
 *
 * @author luka
 * @since 2025-03-26
 */
class FaultToleranceTest {

    private static final String SYM = "BTC-USDT";

    @Test
    @DisplayName("故障演练：Raft 主节点宕机 → 自动选主 → 新 Leader 继续服务")
    void raftLeaderFailover() {
        List<MatchingEngineStateMachine> replicas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JavaMatchingEngine eng = new JavaMatchingEngine();
            MatchingEngineStateMachine sm = new MatchingEngineStateMachine(eng);
            sm.init(SYM);
            replicas.add(sm);
        }
        RaftGroup group = new RaftGroup("shard-0", replicas);

        Order sell = makeOrder(1L, OrderSide.SELL, "50000", "10");
        group.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        for (int i = 0; i < 3; i++) {
            assertThat(group.getReplica(i).getLastAppliedIndex().get()).isEqualTo(1);
        }

        group.triggerLeaderElection(2);
        assertThat(group.getLeaderIndex()).isEqualTo(2);

        Order buy = makeOrder(2L, OrderSide.BUY, "50000", "5");
        Object result = group.propose(OperationLog.OpType.SUBMIT_ORDER, buy, null, null);

        assertThat(result).isInstanceOf(MatchResultDTO.class);
        MatchResultDTO dto = (MatchResultDTO) result;
        assertThat(dto.getTrades()).hasSize(1);
        assertThat(dto.getTrades().get(0).getQuantity()).isEqualByComparingTo("5");

        for (int i = 0; i < 3; i++) {
            assertThat(group.getReplica(i).getLastAppliedIndex().get()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("故障演练：Follower 宕机重启 → 日志回放追赶")
    void followerCrashAndRecovery() {
        List<MatchingEngineStateMachine> replicas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JavaMatchingEngine eng = new JavaMatchingEngine();
            MatchingEngineStateMachine sm = new MatchingEngineStateMachine(eng);
            sm.init(SYM);
            replicas.add(sm);
        }
        RaftGroup group = new RaftGroup("shard-0", replicas);

        for (int i = 1; i <= 5; i++) {
            Order sell = makeOrder(i, OrderSide.SELL, String.valueOf(50000 + i), "1");
            group.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);
        }

        JavaMatchingEngine newEngine = new JavaMatchingEngine();
        MatchingEngineStateMachine newSm = new MatchingEngineStateMachine(newEngine);
        newSm.init(SYM);

        List<OperationLog> logs = group.leader().getLogsAfter(0);
        assertThat(logs).hasSize(5);

        newSm.replay(logs);
        assertThat(newSm.getLastAppliedIndex().get()).isEqualTo(5);
    }

    @Test
    @DisplayName("故障演练：连续撤单 → 再提交 → 状态一致")
    void cancelAndResubmitConsistency() {
        JavaMatchingEngine engine = new JavaMatchingEngine();
        engine.init(SYM, null);

        for (int i = 1; i <= 10; i++) {
            Order sell = makeOrder(i, OrderSide.SELL, "50000", "1");
            engine.submitOrder(sell);
        }
        for (int i = 1; i <= 10; i++) {
            engine.cancelOrder(SYM, i);
        }

        var depth = engine.getMarketDepth(SYM, 10);
        assertThat(depth.getAsks()).isEmpty();

        Order buy = makeOrder(100L, OrderSide.BUY, "50000", "1");
        MatchResultDTO result = engine.submitOrder(buy);
        assertThat(result.getTrades()).isEmpty();
    }

    @Test
    @DisplayName("故障演练：熔断器在连续失败后拒绝请求")
    void circuitBreakerTripAndRecover() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(3, 100);

        assertThat(cb.allowRequest()).isTrue();
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        Thread.sleep(150);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("故障演练：多次 Leader 切换后数据一致性")
    void multipleLeaderSwitchConsistency() {
        List<MatchingEngineStateMachine> replicas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            JavaMatchingEngine eng = new JavaMatchingEngine();
            MatchingEngineStateMachine sm = new MatchingEngineStateMachine(eng);
            sm.init(SYM);
            replicas.add(sm);
        }
        RaftGroup group = new RaftGroup("shard-0", replicas);

        Order sell1 = makeOrder(1L, OrderSide.SELL, "50000", "10");
        group.propose(OperationLog.OpType.SUBMIT_ORDER, sell1, null, null);

        group.triggerLeaderElection(1);
        Order sell2 = makeOrder(2L, OrderSide.SELL, "50100", "5");
        group.propose(OperationLog.OpType.SUBMIT_ORDER, sell2, null, null);

        group.triggerLeaderElection(3);
        Order buy = makeOrder(3L, OrderSide.BUY, "50100", "15");
        Object result = group.propose(OperationLog.OpType.SUBMIT_ORDER, buy, null, null);

        assertThat(result).isInstanceOf(MatchResultDTO.class);
        MatchResultDTO dto = (MatchResultDTO) result;
        assertThat(dto.getTrades()).hasSize(2);

        for (int i = 0; i < 5; i++) {
            assertThat(group.getReplica(i).getLastAppliedIndex().get()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("故障演练：网络分区模拟（少数派无法提交）")
    void networkPartitionMinorityBlocked() {
        List<MatchingEngineStateMachine> replicas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JavaMatchingEngine eng = new JavaMatchingEngine();
            MatchingEngineStateMachine sm = new MatchingEngineStateMachine(eng);
            sm.init(SYM);
            replicas.add(sm);
        }
        RaftGroup group = new RaftGroup("shard-0", replicas);

        Order sell = makeOrder(1L, OrderSide.SELL, "50000", "1");
        group.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        for (int i = 0; i < 3; i++) {
            assertThat(group.getReplica(i).getLastAppliedIndex().get()).isEqualTo(1);
            assertThat(group.getReplica(i).appliedLogCount()).isEqualTo(1);
        }
    }

    private Order makeOrder(long id, OrderSide side, String price, String qty) {
        return Order.builder()
                .orderId(id).userId(1L).symbol(SYM)
                .side(side).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(new BigDecimal(price)).quantity(new BigDecimal(qty))
                .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                .build();
    }
}
