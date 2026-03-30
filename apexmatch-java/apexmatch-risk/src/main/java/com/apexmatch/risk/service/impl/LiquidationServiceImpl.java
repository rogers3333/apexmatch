package com.apexmatch.risk.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.Position;
import com.apexmatch.risk.service.AdlService;
import com.apexmatch.risk.service.InsuranceFundService;
import com.apexmatch.risk.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 强平引擎实现。
 * 保证金率低于维持保证金率（默认 5%）时触发强平。
 * 强平执行流程：
 * 1. 收取强平费（0.5%）到保险基金
 * 2. 结算 PnL
 * 3. 如亏损超出账户余额，由保险基金兜底
 * 4. 清零持仓和冻结保证金
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class LiquidationServiceImpl implements LiquidationService {

    /** 维持保证金率阈值 */
    private static final BigDecimal MAINTENANCE_MARGIN_RATE = new BigDecimal("0.05");
    /** 强平费率（扣给保险基金） */
    private static final BigDecimal LIQUIDATION_FEE_RATE = new BigDecimal("0.005");
    private static final String DEFAULT_CURRENCY = "USDT";

    private final AccountService accountService;
    private final PositionService positionService;
    private final InsuranceFundService insuranceFundService;
    private final AdlService adlService;

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

        BigDecimal closedQty = pos.getQuantity().abs();

        // 1. 计算强平费用（按持仓价值的 0.5%）
        BigDecimal entryPrice = pos.getQuantity().signum() > 0 ? pos.getLongEntryPrice() : pos.getShortEntryPrice();
        BigDecimal positionValue = entryPrice.multiply(closedQty);
        BigDecimal liquidationFee = positionValue.multiply(LIQUIDATION_FEE_RATE).setScale(8, RoundingMode.HALF_UP);

        // 2. 计算未实现 PnL
        BigDecimal pnl = positionService.calculateUnrealizedPnl(pos, markPrice);

        // 3. 获取当前账户余额
        BigDecimal availableBalance = accountService.getAvailableBalance(userId, DEFAULT_CURRENCY);
        BigDecimal frozenBalance = accountService.getAccount(userId, DEFAULT_CURRENCY).getFrozenBalance();

        // 4. 收取强平费（从可用余额中扣取，不足则从保险基金中兜底）
        BigDecimal actualFee = liquidationFee.min(availableBalance.max(BigDecimal.ZERO));
        if (actualFee.signum() > 0) {
            accountService.debit(userId, DEFAULT_CURRENCY, actualFee, null, null);
        }
        if (actualFee.compareTo(liquidationFee) < 0) {
            // 可用余额不足以支付强平费，从保险基金垫付
            insuranceFundService.collectLiquidationFee(DEFAULT_CURRENCY, BigDecimal.ZERO, userId, symbol);
        } else {
            insuranceFundService.collectLiquidationFee(DEFAULT_CURRENCY, actualFee, userId, symbol);
        }

        // 5. 结算 PnL
        if (pnl.signum() < 0) {
            // 亏损
            BigDecimal loss = pnl.abs();
            BigDecimal currentBalance = accountService.getAvailableBalance(userId, DEFAULT_CURRENCY);

            if (loss.compareTo(currentBalance) <= 0) {
                // 余额足够兜底
                accountService.debit(userId, DEFAULT_CURRENCY, loss, null, null);
            } else {
                // 余额不够，先清零账户，剩余亏损由保险基金兜底
                if (currentBalance.signum() > 0) {
                    accountService.debit(userId, DEFAULT_CURRENCY, currentBalance, null, null);
                }
                BigDecimal remainingLoss = loss.subtract(currentBalance);
                insuranceFundService.coverLoss(DEFAULT_CURRENCY, remainingLoss, userId, symbol);
                log.warn("强平亏损超出余额，保险基金兜底 userId={} symbol={} remainingLoss={}",
                        userId, symbol, remainingLoss);

                // 检查是否需要触发 ADL
                if (adlService.shouldTriggerAdl(DEFAULT_CURRENCY)) {
                    BigDecimal coveredByAdl = adlService.executeAdl(symbol, remainingLoss, markPrice, DEFAULT_CURRENCY);
                    log.warn("保险基金不足，触发 ADL symbol={} coveredByAdl={}", symbol, coveredByAdl);
                }
            }
        } else if (pnl.signum() > 0) {
            // 盈利，结算给用户
            accountService.credit(userId, DEFAULT_CURRENCY, pnl, null, null);
        }

        // 6. 解冻冻结的保证金
        if (frozenBalance.signum() > 0) {
            accountService.unfreezeMargin(userId, DEFAULT_CURRENCY, frozenBalance);
        }

        // 7. 清零持仓和 SL/TP
        pos.setQuantity(BigDecimal.ZERO);
        pos.setLongQuantity(BigDecimal.ZERO);
        pos.setShortQuantity(BigDecimal.ZERO);
        pos.setUnrealizedPnl(BigDecimal.ZERO);
        pos.setStopLossPrice(null);
        pos.setTakeProfitPrice(null);

        log.info("强平执行完成 userId={} symbol={} closedQty={} pnl={} liquidationFee={}",
                userId, symbol, closedQty, pnl, liquidationFee);
    }
}

