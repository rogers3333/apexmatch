package com.apexmatch.ha.raft;

import com.apexmatch.common.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Raft 操作日志条目，每条代表一个确定性操作。
 * 被 Leader 复制到 Follower 后由状态机执行。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog implements Serializable {

    public enum OpType {
        SUBMIT_ORDER,
        CANCEL_ORDER,
        SNAPSHOT
    }

    private long logIndex;
    private long term;
    private OpType type;

    /** SUBMIT_ORDER 时携带的订单 */
    private Order order;

    /** CANCEL_ORDER 时的交易对 */
    private String symbol;

    /** CANCEL_ORDER 时的订单 ID */
    private Long orderId;

    private long timestamp;

    public static OperationLog submit(long index, long term, Order order) {
        return OperationLog.builder()
                .logIndex(index).term(term)
                .type(OpType.SUBMIT_ORDER)
                .order(order)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static OperationLog cancel(long index, long term, String symbol, long orderId) {
        return OperationLog.builder()
                .logIndex(index).term(term)
                .type(OpType.CANCEL_ORDER)
                .symbol(symbol).orderId(orderId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
