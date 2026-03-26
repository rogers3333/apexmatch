package com.apexmatch.account.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.MarginMode;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.PositionMode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持仓服务内存实现。
 * 单向模式：quantity 正=多头，负=空头；
 * 双向模式：longQuantity / shortQuantity 分别记录。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class PositionServiceImpl implements PositionService {

    private static final int SCALE = 8;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(18, RM);

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    private String key(long userId, String symbol) {
        return userId + ":" + symbol;
    }

    @Override
    public Position getOrCreatePosition(long userId, String symbol) {
        return positions.computeIfAbsent(key(userId, symbol),
                k -> Position.builder()
                        .userId(userId)
                        .symbol(symbol)
                        .quantity(BigDecimal.ZERO)
                        .longQuantity(BigDecimal.ZERO)
                        .shortQuantity(BigDecimal.ZERO)
                        .longEntryPrice(BigDecimal.ZERO)
                        .shortEntryPrice(BigDecimal.ZERO)
                        .marginMode(MarginMode.CROSS)
                        .positionMode(PositionMode.ONE_WAY)
                        .isolatedMargin(BigDecimal.ZERO)
                        .unrealizedPnl(BigDecimal.ZERO)
                        .leverage(10)
                        .build());
    }

    @Override
    public synchronized void updateOnTrade(long userId, String symbol, OrderSide side,
                                           BigDecimal qty, BigDecimal price, int leverage) {
        Position pos = getOrCreatePosition(userId, symbol);
        pos.setLeverage(leverage);

        if (pos.getPositionMode() == PositionMode.ONE_WAY) {
            updateOneWay(pos, side, qty, price);
        } else {
            updateHedge(pos, side, qty, price);
        }
        log.debug("持仓更新 userId={} symbol={} qty={} entryLong={} entryShort={}",
                userId, symbol, pos.getQuantity(), pos.getLongEntryPrice(), pos.getShortEntryPrice());
    }

    /**
     * 单向持仓：买入增加 quantity（正），卖出减少 quantity（负）。
     * 开仓均价采用加权平均；反向开仓先平现有仓位再建新仓。
     */
    private void updateOneWay(Position pos, OrderSide side, BigDecimal qty, BigDecimal price) {
        BigDecimal current = pos.getQuantity();
        boolean isBuy = side == OrderSide.BUY;
        BigDecimal signed = isBuy ? qty : qty.negate();
        BigDecimal newQty = current.add(signed);

        boolean sameDirection = (isBuy && current.signum() >= 0) || (!isBuy && current.signum() <= 0);
        if (sameDirection) {
            BigDecimal oldEntry = isBuy ? pos.getLongEntryPrice() : pos.getShortEntryPrice();
            BigDecimal avgPrice = weightedAvg(current.abs(), oldEntry, qty, price);
            if (isBuy) {
                pos.setLongEntryPrice(avgPrice);
            } else {
                pos.setShortEntryPrice(avgPrice);
            }
        } else {
            if (newQty.signum() != current.signum() && newQty.signum() != 0) {
                if (newQty.signum() > 0) {
                    pos.setLongEntryPrice(price);
                    pos.setShortEntryPrice(BigDecimal.ZERO);
                } else {
                    pos.setShortEntryPrice(price);
                    pos.setLongEntryPrice(BigDecimal.ZERO);
                }
            }
        }
        pos.setQuantity(newQty);
    }

    /** 双向持仓：多空仓位分别独立管理。 */
    private void updateHedge(Position pos, OrderSide side, BigDecimal qty, BigDecimal price) {
        if (side == OrderSide.BUY) {
            BigDecimal avgPrice = weightedAvg(pos.getLongQuantity(), pos.getLongEntryPrice(), qty, price);
            pos.setLongQuantity(pos.getLongQuantity().add(qty));
            pos.setLongEntryPrice(avgPrice);
        } else {
            BigDecimal avgPrice = weightedAvg(pos.getShortQuantity(), pos.getShortEntryPrice(), qty, price);
            pos.setShortQuantity(pos.getShortQuantity().add(qty));
            pos.setShortEntryPrice(avgPrice);
        }
    }

    @Override
    public BigDecimal calculateUnrealizedPnl(Position pos, BigDecimal markPrice) {
        if (pos.getPositionMode() == PositionMode.ONE_WAY) {
            BigDecimal qty = pos.getQuantity();
            if (qty.signum() == 0) return BigDecimal.ZERO;
            BigDecimal entry = qty.signum() > 0 ? pos.getLongEntryPrice() : pos.getShortEntryPrice();
            return markPrice.subtract(entry).multiply(qty, MC).setScale(SCALE, RM);
        }
        BigDecimal longPnl = pos.getLongQuantity().signum() == 0 ? BigDecimal.ZERO
                : markPrice.subtract(pos.getLongEntryPrice()).multiply(pos.getLongQuantity(), MC);
        BigDecimal shortPnl = pos.getShortQuantity().signum() == 0 ? BigDecimal.ZERO
                : pos.getShortEntryPrice().subtract(markPrice).multiply(pos.getShortQuantity(), MC);
        return longPnl.add(shortPnl).setScale(SCALE, RM);
    }

    @Override
    public BigDecimal calculateMarginRatio(long userId, String currency,
                                           BigDecimal markPrice, AccountService accountService) {
        Account account = accountService.getAccount(userId, currency);
        BigDecimal totalEquity = account.totalEquity();

        BigDecimal usedMargin = account.getFrozenBalance();
        if (usedMargin.signum() == 0) {
            return BigDecimal.ONE;
        }
        return totalEquity.divide(usedMargin, SCALE, RM);
    }

    @Override
    public BigDecimal calculateLiquidationPrice(Position pos, BigDecimal isolatedMargin) {
        BigDecimal qty = pos.getQuantity();
        if (qty.signum() == 0) return BigDecimal.ZERO;

        BigDecimal entry = qty.signum() > 0 ? pos.getLongEntryPrice() : pos.getShortEntryPrice();
        // 多头强平价 = entryPrice - margin / qty
        // 空头强平价 = entryPrice + margin / |qty|
        if (qty.signum() > 0) {
            return entry.subtract(isolatedMargin.divide(qty, SCALE, RM));
        } else {
            return entry.add(isolatedMargin.divide(qty.abs(), SCALE, RM));
        }
    }

    private BigDecimal weightedAvg(BigDecimal oldQty, BigDecimal oldPrice,
                                   BigDecimal newQty, BigDecimal newPrice) {
        BigDecimal absOld = oldQty.abs();
        BigDecimal totalQty = absOld.add(newQty);
        if (totalQty.signum() == 0) return BigDecimal.ZERO;
        return absOld.multiply(oldPrice, MC)
                .add(newQty.multiply(newPrice, MC))
                .divide(totalQty, SCALE, RM);
    }
}
