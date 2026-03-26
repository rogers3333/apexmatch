package com.apexmatch.settlement.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 结算服务实现：资金费率收取、日终对账。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final AccountService accountService;

    @Override
    public BigDecimal calculateFundingFee(BigDecimal positionValue, BigDecimal fundingRate) {
        return positionValue.multiply(fundingRate).setScale(8, RoundingMode.HALF_UP);
    }

    @Override
    public void applyFundingFee(long userId, String currency, BigDecimal fundingFee) {
        if (fundingFee.signum() > 0) {
            accountService.debit(userId, currency, fundingFee, null, null);
        } else if (fundingFee.signum() < 0) {
            accountService.credit(userId, currency, fundingFee.abs(), null, null);
        }
        log.info("资金费率收取 userId={} fee={}", userId, fundingFee);
    }

    @Override
    public boolean reconcile(long userId, String currency) {
        Account account = accountService.getAccount(userId, currency);
        List<FundLedgerEntry> ledger = accountService.getLedger(userId, currency);

        BigDecimal expectedBalance = BigDecimal.ZERO;
        for (FundLedgerEntry entry : ledger) {
            expectedBalance = expectedBalance.add(entry.getAmount());
        }

        BigDecimal actualBalance = account.getBalance();
        boolean matched = actualBalance.compareTo(expectedBalance) == 0;
        if (!matched) {
            log.warn("对账异常 userId={} expected={} actual={}", userId, expectedBalance, actualBalance);
        } else {
            log.info("对账通过 userId={} balance={}", userId, actualBalance);
        }
        return matched;
    }
}
