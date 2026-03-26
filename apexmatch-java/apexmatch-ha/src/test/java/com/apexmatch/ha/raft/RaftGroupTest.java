package com.apexmatch.ha.raft;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.engine.java.JavaMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RaftGroupTest {

    private static final String SYM = "BTC-USDT";
    private RaftGroup raftGroup;

    @BeforeEach
    void setUp() {
        List<MatchingEngineStateMachine> replicas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JavaMatchingEngine engine = new JavaMatchingEngine();
            MatchingEngineStateMachine sm = new MatchingEngineStateMachine(engine);
            sm.init(SYM);
            replicas.add(sm);
        }
        raftGroup = new RaftGroup("shard-0", replicas);
    }

    @Test
    void submitOrderReplicatedToAll() {
        Order sell = makeOrder(1L, OrderSide.SELL, "100", "10");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        for (int i = 0; i < 3; i++) {
            assertThat(raftGroup.getReplica(i).getLastAppliedIndex().get()).isEqualTo(1);
        }
    }

    @Test
    void matchResultConsistentAcrossReplicas() {
        Order sell = makeOrder(1L, OrderSide.SELL, "100", "10");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        Order buy = makeOrder(2L, OrderSide.BUY, "100", "10");
        Object result = raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, buy, null, null);

        assertThat(result).isInstanceOf(MatchResultDTO.class);
        MatchResultDTO dto = (MatchResultDTO) result;
        assertThat(dto.getTrades()).hasSize(1);

        for (int i = 0; i < 3; i++) {
            assertThat(raftGroup.getReplica(i).getLastAppliedIndex().get()).isEqualTo(2);
        }
    }

    @Test
    void cancelOrderReplicatedToAll() {
        Order sell = makeOrder(1L, OrderSide.SELL, "100", "10");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        Object result = raftGroup.propose(OperationLog.OpType.CANCEL_ORDER, null, SYM, 1L);
        assertThat(result).isEqualTo(true);

        for (int i = 0; i < 3; i++) {
            assertThat(raftGroup.getReplica(i).getLastAppliedIndex().get()).isEqualTo(2);
        }
    }

    @Test
    void leaderElectionSwitchesLeader() {
        Order sell = makeOrder(1L, OrderSide.SELL, "100", "10");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        assertThat(raftGroup.getLeaderIndex()).isEqualTo(0);
        raftGroup.triggerLeaderElection(1);
        assertThat(raftGroup.getLeaderIndex()).isEqualTo(1);

        Order sell2 = makeOrder(2L, OrderSide.SELL, "100", "5");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell2, null, null);

        for (int i = 0; i < 3; i++) {
            assertThat(raftGroup.getReplica(i).getLastAppliedIndex().get()).isEqualTo(2);
        }
    }

    @Test
    void followerCatchUpViaReplay() {
        JavaMatchingEngine lateEngine = new JavaMatchingEngine();
        MatchingEngineStateMachine lateSm = new MatchingEngineStateMachine(lateEngine);
        lateSm.init(SYM);

        Order sell = makeOrder(1L, OrderSide.SELL, "100", "10");
        raftGroup.propose(OperationLog.OpType.SUBMIT_ORDER, sell, null, null);

        List<OperationLog> logs = raftGroup.leader().getLogsAfter(0);
        lateSm.replay(logs);

        assertThat(lateSm.getLastAppliedIndex().get()).isEqualTo(1);
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
