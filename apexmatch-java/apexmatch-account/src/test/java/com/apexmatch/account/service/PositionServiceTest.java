package com.apexmatch.account.service;

import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PositionServiceTest {

    private PositionService positionService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        positionService = new PositionServiceImpl();
        accountService = new AccountServiceImpl();
    }

    @Test
    void openLongPosition() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("2"), new BigDecimal("50000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(pos.getLongEntryPrice()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    void openShortPosition() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.SELL,
                new BigDecimal("3"), new BigDecimal("48000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("-3"));
    }

    @Test
    void addToExistingPosition() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("2"), new BigDecimal("50000"), 10);
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("3"), new BigDecimal("52000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        assertThat(pos.getQuantity()).isEqualByComparingTo(new BigDecimal("5"));
        // 加权均价 = (2*50000 + 3*52000) / 5 = 51200
        assertThat(pos.getLongEntryPrice()).isEqualByComparingTo(new BigDecimal("51200"));
    }

    @Test
    void closePosition() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("5"), new BigDecimal("50000"), 10);
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.SELL,
                new BigDecimal("5"), new BigDecimal("51000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        assertThat(pos.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unrealizedPnl_long() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("2"), new BigDecimal("50000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        BigDecimal pnl = positionService.calculateUnrealizedPnl(pos, new BigDecimal("52000"));
        // (52000 - 50000) * 2 = 4000
        assertThat(pnl).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void unrealizedPnl_short() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.SELL,
                new BigDecimal("2"), new BigDecimal("50000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        BigDecimal pnl = positionService.calculateUnrealizedPnl(pos, new BigDecimal("48000"));
        // (48000 - 50000) * (-2) = 4000
        assertThat(pnl).isEqualByComparingTo(new BigDecimal("4000"));
    }

    @Test
    void marginRatio() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("10000"));
        accountService.freezeMargin(1L, "USDT", new BigDecimal("5000"));

        BigDecimal ratio = positionService.calculateMarginRatio(
                1L, "USDT", new BigDecimal("50000"), accountService);
        // totalEquity = 5000 + 5000 = 10000, usedMargin = 5000 → ratio = 2
        assertThat(ratio).isEqualByComparingTo(new BigDecimal("2"));
    }

    @Test
    void liquidationPrice_long() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);
        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        BigDecimal liqPrice = positionService.calculateLiquidationPrice(
                pos, new BigDecimal("5000"));
        // 50000 - 5000/1 = 45000
        assertThat(liqPrice).isEqualByComparingTo(new BigDecimal("45000"));
    }
}
