package com.apexmatch.settlement.service;

import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.settlement.service.impl.ClearingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ClearingServiceTest {

    private ClearingService clearingService;
    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
        PositionServiceImpl positionService = new PositionServiceImpl();
        clearingService = new ClearingServiceImpl(accountService, positionService);

        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100000"));
        accountService.createAccount(2L, "USDT");
        accountService.deposit(2L, "USDT", new BigDecimal("100000"));
    }

    @Test
    void clearTradeDeductsFees() {
        Trade trade = Trade.builder()
                .tradeId(1L)
                .symbol("BTC-USDT")
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1"))
                .takerOrderId(100L)
                .makerOrderId(200L)
                .takerUserId(1L)
                .makerUserId(2L)
                .tradeTime(System.currentTimeMillis())
                .build();

        clearingService.clearTrade(trade, 10);

        // turnover = 50000, taker fee = 50000 * 0.0004 = 20, maker fee = 50000 * 0.0002 = 10
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("99980"));
        assertThat(accountService.getAvailableBalance(2L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("99990"));
    }

    @Test
    void calculateFee() {
        BigDecimal takerFee = clearingService.calculateFee(new BigDecimal("100000"), false);
        BigDecimal makerFee = clearingService.calculateFee(new BigDecimal("100000"), true);
        assertThat(takerFee).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(makerFee).isEqualByComparingTo(new BigDecimal("20"));
    }
}
