package com.apexmatch.risk.service;

import java.math.BigDecimal;

/**
 * ADL (Auto-Deleveraging) 自动减仓服务。
 * 当保险基金不足以覆盖强平亏损时，系统自动减少盈利最多的对手方持仓。
 *
 * @author luka
 * @since 2025-03-30
 */
public interface AdlService {

    /**
     * 执行 ADL 减仓。
     * 从盈利最多的对手方持仓中按优先级队列依次减仓，直到覆盖目标亏损。
     *
     * @param symbol 交易对
     * @param targetLoss 需要覆盖的亏损金额
     * @param markPrice 标记价格
     * @param currency 币种
     * @return 实际覆盖的亏损金额
     */
    BigDecimal executeAdl(String symbol, BigDecimal targetLoss, BigDecimal markPrice, String currency);

    /**
     * 检查是否需要触发 ADL。
     * 当保险基金余额低于阈值时返回 true。
     *
     * @param currency 币种
     * @return 是否需要触发 ADL
     */
    boolean shouldTriggerAdl(String currency);
}
