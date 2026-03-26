package com.apexmatch.risk.service;

import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.common.exception.ApexMatchException;
import com.apexmatch.risk.service.impl.RiskControlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class RiskControlServiceTest {

    private RiskControlService riskControlService;
    private AccountServiceImpl accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
        riskControlService = new RiskControlServiceImpl(accountService);
    }

    @Test
    void preTradeCheckPasses() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100000"));

        Order order = Order.builder()
                .orderId(1L)
                .userId(1L)
                .symbol("BTC-USDT")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1"))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .build();

        assertThatNoException().isThrownBy(
                () -> riskControlService.preTradeCheck(order, 10));
    }

    @Test
    void preTradeCheckFailsInsufficientMargin() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100"));

        Order order = Order.builder()
                .orderId(1L)
                .userId(1L)
                .symbol("BTC-USDT")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1"))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .build();

        // required = 50000 * 1 / 10 = 5000, available = 100
        assertThatThrownBy(() -> riskControlService.preTradeCheck(order, 10))
                .isInstanceOf(ApexMatchException.class);
    }

    @Test
    void calculateRequiredMargin() {
        Order order = Order.builder()
                .orderId(1L).userId(1L).symbol("BTC-USDT")
                .side(OrderSide.BUY).type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("2"))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .build();

        BigDecimal margin = riskControlService.calculateRequiredMargin(order, 20);
        // 50000 * 2 / 20 = 5000
        assertThat(margin).isEqualByComparingTo(new BigDecimal("5000"));
    }
}
