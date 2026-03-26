package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WAL 日志条目。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalEntry {

    public enum Type {
        SUBMIT,
        CANCEL
    }

    private Type type;

    /**
     * SUBMIT 时携带完整订单
     */
    private Order order;

    /**
     * CANCEL 时的交易对
     */
    private String symbol;

    /**
     * CANCEL 时的订单号
     */
    private long orderId;

    public static WalEntry ofSubmit(Order order) {
        return WalEntry.builder().type(Type.SUBMIT).order(order).build();
    }

    public static WalEntry ofCancel(String symbol, long orderId) {
        return WalEntry.builder().type(Type.CANCEL).symbol(symbol).orderId(orderId).build();
    }
}
