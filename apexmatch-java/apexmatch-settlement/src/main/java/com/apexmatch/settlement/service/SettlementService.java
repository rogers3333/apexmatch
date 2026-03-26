package com.apexmatch.settlement.service;

import java.math.BigDecimal;

/**
 * 日终结算与隔夜利息服务。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface SettlementService {

    /**
     * 计算隔夜资金费率：持仓价值 × fundingRate。
     * 正费率：多头付空头；负费率：空头付多头。
     */
    BigDecimal calculateFundingFee(BigDecimal positionValue, BigDecimal fundingRate);

    /**
     * 对指定用户执行资金费率收取/支付。
     *
     * @param userId      用户
     * @param currency    币种
     * @param fundingFee  费用（正=扣除，负=获得）
     */
    void applyFundingFee(long userId, String currency, BigDecimal fundingFee);

    /**
     * 日终对账：验证用户账户余额 = 初始余额 + Σ流水金额。
     *
     * @return true 表示对账通过
     */
    boolean reconcile(long userId, String currency);
}
