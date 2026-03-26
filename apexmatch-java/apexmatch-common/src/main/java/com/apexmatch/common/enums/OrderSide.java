package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 订单买卖方向。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum OrderSide {

    /**
     * 买入
     */
    BUY(1),

    /**
     * 卖出
     */
    SELL(2);

    private final int code;

    OrderSide(int code) {
        this.code = code;
    }
}
