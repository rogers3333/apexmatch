package com.apexmatch.account.service;

import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.common.exception.ApexMatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AccountServiceTest {

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
    }

    @Test
    void createAndGetAccount() {
        Account account = accountService.createAccount(1L, "USDT");
        assertThat(account.getUserId()).isEqualTo(1L);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deposit() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("1000"));
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("1000"));
    }

    @Test
    void withdrawSuccess() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("1000"));
        accountService.withdraw(1L, "USDT", new BigDecimal("300"));
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("700"));
    }

    @Test
    void withdrawInsufficientBalance() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100"));
        assertThatThrownBy(() -> accountService.withdraw(1L, "USDT", new BigDecimal("200")))
                .isInstanceOf(ApexMatchException.class);
    }

    @Test
    void freezeAndUnfreeze() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("1000"));
        accountService.freezeMargin(1L, "USDT", new BigDecimal("400"));

        Account acc = accountService.getAccount(1L, "USDT");
        assertThat(acc.getBalance()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(acc.getFrozenBalance()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(acc.totalEquity()).isEqualByComparingTo(new BigDecimal("1000"));

        accountService.unfreezeMargin(1L, "USDT", new BigDecimal("200"));
        acc = accountService.getAccount(1L, "USDT");
        assertThat(acc.getBalance()).isEqualByComparingTo(new BigDecimal("800"));
        assertThat(acc.getFrozenBalance()).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    void freezeInsufficientMargin() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100"));
        assertThatThrownBy(() -> accountService.freezeMargin(1L, "USDT", new BigDecimal("200")))
                .isInstanceOf(ApexMatchException.class);
    }

    @Test
    void debitAndCredit() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("1000"));
        accountService.debit(1L, "USDT", new BigDecimal("50"), 100L, 200L);
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("950"));

        accountService.credit(1L, "USDT", new BigDecimal("30"), 101L, 201L);
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("980"));
    }

    @Test
    void ledgerRecords() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("1000"));
        accountService.debit(1L, "USDT", new BigDecimal("50"), null, null);
        List<FundLedgerEntry> ledger = accountService.getLedger(1L, "USDT");
        assertThat(ledger).hasSize(2);
    }

    @Test
    void accountNotFound() {
        assertThatThrownBy(() -> accountService.getAccount(999L, "USDT"))
                .isInstanceOf(ApexMatchException.class);
    }
}
