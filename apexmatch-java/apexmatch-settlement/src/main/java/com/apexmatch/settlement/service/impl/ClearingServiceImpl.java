package com.apexmatch.settlement.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.settlement.service.ClearingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 实时清算实现。
 * 成交后：
 * 1. 按杠杆冻结/解冻保证金
 * 2. 扣除手续费
 * 3. 更新持仓
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class ClearingServiceImpl implements ClearingService {

    private static final BigDecimal TAKER_FEE_RATE = new BigDecimal("0.0004");
    private static final BigDecimal MAKER_FEE_RATE = new BigDecimal("0.0002");
    private static final String DEFAULT_CURRENCY = "USDT";

    private final AccountService accountService;
    private final PositionService positionService;

    @Override
    public List<FundLedgerEntry> clearTrade(Trade trade, int leverage) {
        BigDecimal turnover = trade.getPrice().multiply(trade.getQuantity());
        BigDecimal margin = turnover.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        BigDecimal takerFee = calculateFee(turnover, false);
        BigDecimal makerFee = calculateFee(turnover, true);

        // Taker 端清算
        accountService.debit(trade.getTakerUserId(), DEFAULT_CURRENCY, takerFee,
                trade.getTakerOrderId(), trade.getTradeId());
        positionService.updateOnTrade(trade.getTakerUserId(), trade.getSymbol(),
                OrderSide.BUY, trade.getQuantity(), trade.getPrice(), leverage);

        // Maker 端清算
        accountService.debit(trade.getMakerUserId(), DEFAULT_CURRENCY, makerFee,
                trade.getMakerOrderId(), trade.getTradeId());
        positionService.updateOnTrade(trade.getMakerUserId(), trade.getSymbol(),
                OrderSide.SELL, trade.getQuantity(), trade.getPrice(), leverage);

        log.info("清算完成 tradeId={} turnover={} takerFee={} makerFee={}",
                trade.getTradeId(), turnover, takerFee, makerFee);

        List<FundLedgerEntry> result = new ArrayList<>();
        result.addAll(accountService.getLedger(trade.getTakerUserId(), DEFAULT_CURRENCY));
        result.addAll(accountService.getLedger(trade.getMakerUserId(), DEFAULT_CURRENCY));
        return result;
    }

    @Override
    public BigDecimal calculateFee(BigDecimal turnover, boolean isMaker) {
        BigDecimal rate = isMaker ? MAKER_FEE_RATE : TAKER_FEE_RATE;
        return turnover.multiply(rate).setScale(8, RoundingMode.HALF_UP);
    }
}
