package com.apexmatch.ha.raft;

import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.EngineInitOptions;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎 Raft 状态机。
 * <p>
 * 每条 {@link OperationLog} 经 Raft 多数确认后，由状态机按序执行，
 * 保证所有副本最终状态一致。可对接 SOFAJRaft 的 {@code StateMachine} 接口。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class MatchingEngineStateMachine {

    private final MatchingEngine engine;

    @Getter
    private final AtomicLong lastAppliedIndex = new AtomicLong(0);

    @Getter
    private final AtomicLong currentTerm = new AtomicLong(0);

    /** 已应用的日志（用于快照与回放） */
    private final CopyOnWriteArrayList<OperationLog> appliedLogs = new CopyOnWriteArrayList<>();

    /** 状态机输出回调 */
    private final List<StateMachineListener> listeners = new CopyOnWriteArrayList<>();

    public MatchingEngineStateMachine(MatchingEngine engine) {
        this.engine = engine;
    }

    /**
     * 初始化交易对（在 Raft 组启动时调用）。
     */
    public void init(String symbol) {
        engine.init(symbol, EngineInitOptions.builder().build());
    }

    /**
     * 应用一条已提交的日志。
     * 在生产环境中由 JRaft 的 {@code onApply(Iterator)} 回调驱动。
     */
    public Object onApply(OperationLog logEntry) {
        if (logEntry.getLogIndex() <= lastAppliedIndex.get()) {
            log.warn("跳过已应用日志 index={}", logEntry.getLogIndex());
            return null;
        }

        Object result = switch (logEntry.getType()) {
            case SUBMIT_ORDER -> {
                Order copy = copyOrder(logEntry.getOrder());
                MatchResultDTO r = engine.submitOrder(copy);
                yield r;
            }
            case CANCEL_ORDER -> {
                Optional<Boolean> r = engine.cancelOrder(logEntry.getSymbol(), logEntry.getOrderId());
                yield r.orElse(false);
            }
            case SNAPSHOT -> {
                log.info("快照标记 index={}", logEntry.getLogIndex());
                yield null;
            }
        };

        lastAppliedIndex.set(logEntry.getLogIndex());
        currentTerm.set(logEntry.getTerm());
        appliedLogs.add(logEntry);

        for (StateMachineListener listener : listeners) {
            listener.onApplied(logEntry, result);
        }

        return result;
    }

    /**
     * 批量回放日志（用于 Follower 追赶或重启恢复）。
     */
    public void replay(List<OperationLog> logs) {
        for (OperationLog entry : logs) {
            onApply(entry);
        }
        log.info("日志回放完成，lastApplied={}", lastAppliedIndex.get());
    }

    /**
     * 获取快照数据：从 lastAppliedIndex 之后开始增量复制。
     */
    public List<OperationLog> getLogsAfter(long fromIndex) {
        List<OperationLog> result = new ArrayList<>();
        for (OperationLog entry : appliedLogs) {
            if (entry.getLogIndex() > fromIndex) {
                result.add(entry);
            }
        }
        return result;
    }

    public int appliedLogCount() {
        return appliedLogs.size();
    }

    public void addListener(StateMachineListener listener) {
        listeners.add(listener);
    }

    private Order copyOrder(Order src) {
        return Order.builder()
                .orderId(src.getOrderId())
                .clientOrderId(src.getClientOrderId())
                .userId(src.getUserId())
                .symbol(src.getSymbol())
                .side(src.getSide())
                .type(src.getType())
                .timeInForce(src.getTimeInForce())
                .price(src.getPrice())
                .quantity(src.getQuantity())
                .filledQuantity(src.getFilledQuantity())
                .triggerPrice(src.getTriggerPrice())
                .takeProfitPrice(src.getTakeProfitPrice())
                .stopLossPrice(src.getStopLossPrice())
                .status(src.getStatus())
                .sequenceTime(src.getSequenceTime())
                .displayQuantity(src.getDisplayQuantity())
                .build();
    }

    /**
     * 状态机事件监听器。
     */
    @FunctionalInterface
    public interface StateMachineListener {
        void onApplied(OperationLog log, Object result);
    }
}
