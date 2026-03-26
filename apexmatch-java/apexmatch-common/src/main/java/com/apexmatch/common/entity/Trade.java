package com.apexmatch.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单笔成交记录。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    /**
     * 成交 ID
     */
    private Long tradeId;

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 成交价格
     */
    private BigDecimal price;

    /**
     * 成交数量
     */
    private BigDecimal quantity;

    /**
     * Taker 订单 ID
     */
    private Long takerOrderId;

    /**
     * Maker 订单 ID
     */
    private Long makerOrderId;

    /**
     * Taker 用户 ID
     */
    private Long takerUserId;

    /**
     * Maker 用户 ID
     */
    private Long makerUserId;

    /**
     * 成交时间（毫秒）
     */
    private long tradeTime;
}
