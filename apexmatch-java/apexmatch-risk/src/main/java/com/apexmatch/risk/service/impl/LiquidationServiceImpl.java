package com.apexmatch.risk.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.Position;
import com.apexmatch.risk.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 强平引擎实现。
 * 保证金率低于维持保证金率（默认 5%）时触发强平。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class LiquidationServiceImpl implements LiquidationService {

    /** 维持保证金率阈值 */
    private static final BigDecimal MAINTENANCE_MARGIN_RATE = new BigDecimal("0.05");
    private static final String DEFAULT_CURRENCY = "USDT";

    private final AccountService accountService;
    private final PositionService positionService;

    @Override
    public boolean checkLiquidation(long userId, String symbol, BigDecimal markPrice) {
        Position pos = positionService.getOrCreatePosition(userId, symbol);
        if (pos.getQuantity().signum() == 0) {
            return false;
        }
        BigDecimal marginRatio = positionService.calculateMarginRatio(
                userId, DEFAULT_CURRENCY, markPrice, accountService);
        boolean triggered = marginRatio.compareTo(MAINTENANCE_MARGIN_RATE) <= 0;
        if (triggered) {
            log.warn("强平触发 userId={} symbol={} marginRatio={} threshold={}",
                    userId, symbol, marginRatio, MAINTENANCE_MARGIN_RATE);
        }
        return triggered;
    }

    @Override
    public void executeLiquidation(long userId, String symbol, BigDecimal markPrice) {
        Position pos = positionService.getOrCreatePosition(userId, symbol);
        if (pos.getQuantity().signum() == 0) {
            return;
        }

        BigDecimal pnl = positionService.calculateUnrealizedPnl(pos, markPrice);
        if (pnl.signum() < 0) {
            accountService.debit(userId, DEFAULT_CURRENCY, pnl.abs(), null, null);
        } else if (pnl.signum() > 0) {
            accountService.credit(userId, DEFAULT_CURRENCY, pnl, null, null);
        }

        BigDecimal closedQty = pos.getQuantity().abs();
        pos.setQuantity(BigDecimal.ZERO);
        pos.setLongQuantity(BigDecimal.ZERO);
        pos.setShortQuantity(BigDecimal.ZERO);
        pos.setUnrealizedPnl(BigDecimal.ZERO);

        log.info("强平执行完成 userId={} symbol={} closedQty={} pnl={}", userId, symbol, closedQty, pnl);
    }
}
