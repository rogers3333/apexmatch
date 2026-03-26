package com.apexmatch.risk.service;

import java.math.BigDecimal;

/**
 * 强制平仓服务。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface LiquidationService {

    /**
     * 检查用户是否触发强平条件。
     *
     * @param userId    用户
     * @param symbol    交易对
     * @param markPrice 标记价格
     * @return true 表示需要强平
     */
    boolean checkLiquidation(long userId, String symbol, BigDecimal markPrice);

    /**
     * 执行强平：市价平掉指定交易对的全部仓位。
     *
     * @param userId    用户
     * @param symbol    交易对
     * @param markPrice 标记价格
     */
    void executeLiquidation(long userId, String symbol, BigDecimal markPrice);
}
