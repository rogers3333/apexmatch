package com.apexmatch.settlement.service;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.settlement.service.impl.SettlementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class SettlementServiceTest {

    private SettlementService settlementService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
        settlementService = new SettlementServiceImpl(accountService);
    }

    @Test
    void fundingFeeCalculation() {
        BigDecimal fee = settlementService.calculateFundingFee(
                new BigDecimal("100000"), new BigDecimal("0.0001"));
        assertThat(fee).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void applyPositiveFundingFee() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("10000"));
        settlementService.applyFundingFee(1L, "USDT", new BigDecimal("50"));
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("9950"));
    }

    @Test
    void applyNegativeFundingFee() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("10000"));
        settlementService.applyFundingFee(1L, "USDT", new BigDecimal("-50"));
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("10050"));
    }

    @Test
    void reconcileSuccess() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("10000"));
        accountService.debit(1L, "USDT", new BigDecimal("100"), null, null);
        assertThat(settlementService.reconcile(1L, "USDT")).isTrue();
    }
}
