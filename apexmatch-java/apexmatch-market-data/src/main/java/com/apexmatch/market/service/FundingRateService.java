package com.apexmatch.market.service;

import java.math.BigDecimal;

/**
 * 资金费率服务。
 * 资金费率用于多空双方之间的资金转移，防止合约价格偏离现货价格。
 * 每 8 小时结算一次（00:00, 08:00, 16:00 UTC）。
 *
 * @author luka
 * @since 2025-03-30
 */
public interface FundingRateService {

    /**
     * 获取当前资金费率
     */
    BigDecimal getCurrentFundingRate(String symbol);

    /**
     * 计算并更新资金费率
     * 公式：fundingRate = clamp((markPrice - indexPrice) / indexPrice, -0.75%, 0.75%)
     */
    void calculateFundingRate(String symbol, BigDecimal markPrice, BigDecimal indexPrice);

    /**
     * 获取下次资金费率结算时间（毫秒时间戳）
     */
    long getNextFundingTime(String symbol);
}
