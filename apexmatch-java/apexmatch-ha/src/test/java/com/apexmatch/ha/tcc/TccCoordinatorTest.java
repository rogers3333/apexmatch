package com.apexmatch.ha.tcc;

import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.java.JavaMatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class TccCoordinatorTest {

    private TccCoordinator coordinator;
    private AccountServiceImpl accountService;
    private JavaMatchingEngine engine;

    @BeforeEach
    void setUp() {
        coordinator = new TccCoordinator();
        accountService = new AccountServiceImpl();
        engine = new JavaMatchingEngine();
        engine.init("BTC-USDT", null);
    }

    @Test
    void tccSuccessful() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100000"));

        OrderPlacementTcc tcc = new OrderPlacementTcc(accountService, engine);
        OrderPlacementContext ctx = OrderPlacementContext.builder()
                .order(makeOrder(1L, OrderSide.BUY, "50000", "1"))
                .userId(1L).currency("USDT").leverage(10)
                .build();

        boolean ok = coordinator.execute(tcc, ctx);
        assertThat(ok).isTrue();
        assertThat(ctx.getResult()).isNotNull();
        // 冻结 50000*1/10 = 5000
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("95000"));
    }

    @Test
    void tccRollbackOnInsufficientMargin() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100"));

        OrderPlacementTcc tcc = new OrderPlacementTcc(accountService, engine);
        OrderPlacementContext ctx = OrderPlacementContext.builder()
                .order(makeOrder(1L, OrderSide.BUY, "50000", "1"))
                .userId(1L).currency("USDT").leverage(10)
                .build();

        boolean ok = coordinator.execute(tcc, ctx);
        assertThat(ok).isFalse();
        // 余额应完整恢复
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void tccCancelCallbackInvoked() {
        accountService.createAccount(1L, "USDT");
        accountService.deposit(1L, "USDT", new BigDecimal("100000"));

        // 自定义 TCC：Try 成功但 Confirm 失败
        TccAction<OrderPlacementContext> failConfirm = new TccAction<>() {
            final OrderPlacementTcc delegate = new OrderPlacementTcc(accountService, engine);
            @Override
            public boolean tryAction(OrderPlacementContext ctx) { return delegate.tryAction(ctx); }
            @Override
            public boolean confirmAction(OrderPlacementContext ctx) { return false; }
            @Override
            public boolean cancelAction(OrderPlacementContext ctx) { return delegate.cancelAction(ctx); }
        };

        OrderPlacementContext ctx = OrderPlacementContext.builder()
                .order(makeOrder(1L, OrderSide.BUY, "50000", "1"))
                .userId(1L).currency("USDT").leverage(10)
                .build();

        boolean ok = coordinator.execute(failConfirm, ctx);
        assertThat(ok).isFalse();
        // Cancel 应解冻保证金
        assertThat(accountService.getAvailableBalance(1L, "USDT"))
                .isEqualByComparingTo(new BigDecimal("100000"));
    }

    private Order makeOrder(long id, OrderSide side, String price, String qty) {
        return Order.builder()
                .orderId(id).userId(1L).symbol("BTC-USDT")
                .side(side).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(new BigDecimal(price)).quantity(new BigDecimal(qty))
                .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                .build();
    }
}
