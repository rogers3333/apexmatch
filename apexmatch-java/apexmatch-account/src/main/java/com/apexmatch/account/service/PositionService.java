package com.apexmatch.account.service;

import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.OrderSide;

import java.math.BigDecimal;

/**
 * 持仓管理服务，支持逐仓/全仓、单向/双向持仓。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface PositionService {

    Position getOrCreatePosition(long userId, String symbol);

    /**
     * 成交后更新持仓。
     *
     * @param userId   用户
     * @param symbol   交易对
     * @param side     成交方向
     * @param qty      成交数量
     * @param price    成交价格
     * @param leverage 杠杆倍数
     */
    void updateOnTrade(long userId, String symbol, OrderSide side,
                       BigDecimal qty, BigDecimal price, int leverage);

    /** 计算未实现盈亏 */
    BigDecimal calculateUnrealizedPnl(Position pos, BigDecimal markPrice);

    /**
     * 计算保证金率 = (totalEquity + unrealizedPnl) / usedMargin。
     * 若无仓位返回 {@link BigDecimal#ONE} 表示安全。
     */
    BigDecimal calculateMarginRatio(long userId, String currency,
                                    BigDecimal markPrice, AccountService accountService);

    /** 计算强平价（逐仓模式） */
    BigDecimal calculateLiquidationPrice(Position pos, BigDecimal isolatedMargin);
}
