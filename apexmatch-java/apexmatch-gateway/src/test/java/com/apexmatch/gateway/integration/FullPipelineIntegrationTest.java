package com.apexmatch.gateway.integration;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.entity.*;
import com.apexmatch.common.enums.*;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.engine.java.JavaMatchingEngine;
import com.apexmatch.market.service.KlineService;
import com.apexmatch.market.service.impl.KlineServiceImpl;
import com.apexmatch.settlement.service.ClearingService;
import com.apexmatch.settlement.service.impl.ClearingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 全链路集成测试：下单 → 撮合 → 清算 → 持仓 → 行情。
 *
 * @author luka
 * @since 2025-03-26
 */
class FullPipelineIntegrationTest {

    private static final String SYM = "BTC-USDT";
    private static final String CUR = "USDT";

    private JavaMatchingEngine engine;
    private AccountService accountService;
    private PositionService positionService;
    private ClearingService clearingService;
    private KlineService klineService;

    @BeforeEach
    void setUp() {
        engine = new JavaMatchingEngine();
        engine.init(SYM, null);
        accountService = new AccountServiceImpl();
        positionService = new PositionServiceImpl();
        clearingService = new ClearingServiceImpl(accountService, positionService);
        klineService = new KlineServiceImpl();

        accountService.createAccount(1L, CUR);
        accountService.deposit(1L, CUR, new BigDecimal("100000"));
        accountService.createAccount(2L, CUR);
        accountService.deposit(2L, CUR, new BigDecimal("100000"));
    }

    @Test
    @DisplayName("全链路：限价单完全成交 → 清算 → 余额/持仓/K线验证")
    void fullMatchAndClear() {
        Order sellOrder = makeOrder(1L, 2L, OrderSide.SELL, "50000", "1");
        MatchResultDTO sellResult = engine.submitOrder(sellOrder);
        assertThat(sellResult.getTrades()).isEmpty();

        Order buyOrder = makeOrder(2L, 1L, OrderSide.BUY, "50000", "1");
        MatchResultDTO buyResult = engine.submitOrder(buyOrder);
        assertThat(buyResult.getTrades()).hasSize(1);

        Trade trade = buyResult.getTrades().get(0);
        assertThat(trade.getPrice()).isEqualByComparingTo("50000");
        assertThat(trade.getQuantity()).isEqualByComparingTo("1");

        clearingService.clearTrade(trade, 10);

        BigDecimal buyerBalance = accountService.getAvailableBalance(1L, CUR);
        BigDecimal sellerBalance = accountService.getAvailableBalance(2L, CUR);
        assertThat(buyerBalance).isLessThan(new BigDecimal("100000"));
        assertThat(sellerBalance).isLessThan(new BigDecimal("100000"));

        List<FundLedgerEntry> buyerLedger = accountService.getLedger(1L, CUR);
        assertThat(buyerLedger.size()).isGreaterThanOrEqualTo(2);

        klineService.onTrade(trade);
        Kline kline = klineService.getLatest(SYM, "1m");
        assertThat(kline).isNotNull();
        assertThat(kline.getClose()).isEqualByComparingTo("50000");
        assertThat(kline.getVolume()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("全链路：部分成交 → 多次清算 → 挂单剩余")
    void partialFillPipeline() {
        Order sellOrder = makeOrder(1L, 2L, OrderSide.SELL, "50000", "5");
        engine.submitOrder(sellOrder);

        Order buyOrder = makeOrder(2L, 1L, OrderSide.BUY, "50000", "2");
        MatchResultDTO result = engine.submitOrder(buyOrder);
        assertThat(result.getTrades()).hasSize(1);
        Trade trade = result.getTrades().get(0);
        assertThat(trade.getQuantity()).isEqualByComparingTo("2");

        clearingService.clearTrade(trade, 10);

        var depth = engine.getMarketDepth(SYM, 10);
        assertThat(depth.getAsks()).isNotEmpty();
        assertThat(depth.getAsks().get(0).getQuantity()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("全链路：多用户多订单连续撮合")
    void multiUserContinuousMatch() {
        accountService.createAccount(3L, CUR);
        accountService.deposit(3L, CUR, new BigDecimal("100000"));

        Order sell1 = makeOrder(1L, 2L, OrderSide.SELL, "50000", "1");
        Order sell2 = makeOrder(2L, 3L, OrderSide.SELL, "50100", "1");
        engine.submitOrder(sell1);
        engine.submitOrder(sell2);

        Order bigBuy = makeOrder(3L, 1L, OrderSide.BUY, "50100", "2");
        MatchResultDTO result = engine.submitOrder(bigBuy);
        assertThat(result.getTrades()).hasSize(2);

        for (Trade t : result.getTrades()) {
            clearingService.clearTrade(t, 10);
            klineService.onTrade(t);
        }

        List<Kline> klines = klineService.getKlines(SYM, "1m", 10);
        assertThat(klines).isNotEmpty();
        Kline latest = klines.get(klines.size() - 1);
        assertThat(latest.getTradeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("全链路：市价单即时吃单")
    void marketOrderFill() {
        Order sell = makeOrder(1L, 2L, OrderSide.SELL, "50000", "1");
        engine.submitOrder(sell);

        Order marketBuy = Order.builder()
                .orderId(2L).userId(1L).symbol(SYM)
                .side(OrderSide.BUY).type(OrderType.MARKET).timeInForce(TimeInForce.IOC)
                .quantity(new BigDecimal("1")).filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .build();
        MatchResultDTO result = engine.submitOrder(marketBuy);
        assertThat(result.getTrades()).hasSize(1);

        clearingService.clearTrade(result.getTrades().get(0), 10);

        var depth = engine.getMarketDepth(SYM, 10);
        assertThat(depth.getAsks()).isEmpty();
    }

    @Test
    @DisplayName("全链路：撤单后不再参与撮合")
    void cancelOrderRemovesFromBook() {
        Order sell = makeOrder(1L, 2L, OrderSide.SELL, "50000", "1");
        engine.submitOrder(sell);

        engine.cancelOrder(SYM, 1L);

        Order buy = makeOrder(2L, 1L, OrderSide.BUY, "50000", "1");
        MatchResultDTO result = engine.submitOrder(buy);
        assertThat(result.getTrades()).isEmpty();
    }

    @Test
    @DisplayName("全链路：FOK 全量成交或全部取消")
    void fokOrderAllOrNothing() {
        Order sell = makeOrder(1L, 2L, OrderSide.SELL, "50000", "1");
        engine.submitOrder(sell);

        Order fokBuy = Order.builder()
                .orderId(2L).userId(1L).symbol(SYM)
                .side(OrderSide.BUY).type(OrderType.LIMIT).timeInForce(TimeInForce.FOK)
                .price(new BigDecimal("50000")).quantity(new BigDecimal("5"))
                .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                .build();
        MatchResultDTO result = engine.submitOrder(fokBuy);
        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getAffectedOrder().getStatus()).isIn(OrderStatus.CANCELED, OrderStatus.REJECTED);
    }

    private Order makeOrder(long orderId, long userId, OrderSide side, String price, String qty) {
        return Order.builder()
                .orderId(orderId).userId(userId).symbol(SYM)
                .side(side).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(new BigDecimal(price)).quantity(new BigDecimal(qty))
                .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                .build();
    }
}
