package com.apexmatch.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * K 线（蜡烛图）数据。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Kline {

    private String symbol;

    /** 周期：1m / 5m / 15m / 1h / 4h / 1d */
    private String interval;

    private long openTime;

    private long closeTime;

    private BigDecimal open;

    private BigDecimal high;

    private BigDecimal low;

    private BigDecimal close;

    /** 成交量 */
    @Builder.Default
    private BigDecimal volume = BigDecimal.ZERO;

    /** 成交额 */
    @Builder.Default
    private BigDecimal turnover = BigDecimal.ZERO;

    /** 成交笔数 */
    @Builder.Default
    private int tradeCount = 0;
}
