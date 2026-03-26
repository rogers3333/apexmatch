package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.common.enums.*;
import com.apexmatch.common.util.SnowflakeIdGenerator;
import com.apexmatch.engine.api.dto.MarketDepthDTO;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 撮合引擎综合测试，覆盖：限价单、市价单、FOK、IOC、止损单、冰山单、撤单、盘口深度。
 *
 * @author luka
 * @since 2025-03-26
 */
class JavaMatchingEngineTest {

    private static final String SYMBOL = "BTC-USDT";
    private JavaMatchingEngine engine;
    private final AtomicLong orderIdSeq = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        engine = new JavaMatchingEngine(new SnowflakeIdGenerator(1, 1));
        engine.init(SYMBOL, null);
    }

    // ==================== 限价单基础 ====================

    @Nested
    class LimitOrderTests {

        @Test
        void exactMatch_buyMeetsSell() {
            submit(sellLimit(100, 10));
            MatchResultDTO result = submit(buyLimit(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            Trade trade = result.getTrades().get(0);
            assertThat(trade.getPrice()).isEqualByComparingTo("100");
            assertThat(trade.getQuantity()).isEqualByComparingTo("10");
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(engine.getOrderBook(SYMBOL).size()).isZero();
        }

        @Test
        void exactMatch_sellMeetsBuy() {
            submit(buyLimit(100, 10));
            MatchResultDTO result = submit(sellLimit(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void partialFill_takerPartiallyFilled() {
            submit(sellLimit(100, 5));
            MatchResultDTO result = submit(buyLimit(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getTrades().get(0).getQuantity()).isEqualByComparingTo("5");
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(result.getAffectedOrder().getFilledQuantity()).isEqualByComparingTo("5");
            assertThat(engine.getOrderBook(SYMBOL).size()).isEqualTo(1);
        }

        @Test
        void partialFill_makerPartiallyFilled() {
            submit(sellLimit(100, 10));
            MatchResultDTO result = submit(buyLimit(100, 3));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(engine.getOrderBook(SYMBOL).size()).isEqualTo(1);
        }

        @Test
        void noMatch_priceNotCrossed() {
            submit(sellLimit(110, 10));
            MatchResultDTO result = submit(buyLimit(100, 10));

            assertThat(result.getTrades()).isEmpty();
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(engine.getOrderBook(SYMBOL).size()).isEqualTo(2);
        }

        @Test
        void pricePriority_lowestAskMatchedFirst() {
            submit(sellLimit(102, 5));
            submit(sellLimit(100, 5));
            submit(sellLimit(101, 5));

            MatchResultDTO result = submit(buyLimit(102, 10));

            assertThat(result.getTrades()).hasSize(2);
            assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("100");
            assertThat(result.getTrades().get(1).getPrice()).isEqualByComparingTo("101");
        }

        @Test
        void pricePriority_highestBidMatchedFirst() {
            submit(buyLimit(98, 5));
            submit(buyLimit(100, 5));
            submit(buyLimit(99, 5));

            MatchResultDTO result = submit(sellLimit(98, 10));

            assertThat(result.getTrades()).hasSize(2);
            assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("100");
            assertThat(result.getTrades().get(1).getPrice()).isEqualByComparingTo("99");
        }

        @Test
        void timePriority_fifoWithinSamePrice() {
            Order first = sellLimit(100, 5);
            first.setSequenceTime(1000);
            submit(first);

            Order second = sellLimit(100, 5);
            second.setSequenceTime(2000);
            submit(second);

            MatchResultDTO result = submit(buyLimit(100, 5));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getTrades().get(0).getMakerOrderId()).isEqualTo(first.getOrderId());
        }

        @Test
        void multipleTrades_takerSweeepsMultipleLevels() {
            submit(sellLimit(100, 3));
            submit(sellLimit(101, 3));
            submit(sellLimit(102, 4));

            MatchResultDTO result = submit(buyLimit(102, 10));

            assertThat(result.getTrades()).hasSize(3);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(result.getAffectedOrder().getFilledQuantity()).isEqualByComparingTo("10");
        }

        @Test
        void tradeAtMakerPrice() {
            submit(sellLimit(99, 10));
            MatchResultDTO result = submit(buyLimit(100, 5));

            assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo("99");
        }
    }

    // ==================== 市价单 ====================

    @Nested
    class MarketOrderTests {

        @Test
        void marketBuy_matchesAllAsks() {
            submit(sellLimit(100, 5));
            submit(sellLimit(101, 5));

            MatchResultDTO result = submit(marketBuy(10));

            assertThat(result.getTrades()).hasSize(2);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void marketSell_matchesAllBids() {
            submit(buyLimit(100, 5));
            submit(buyLimit(99, 5));

            MatchResultDTO result = submit(marketSell(7));

            assertThat(result.getTrades()).hasSize(2);
            assertThat(result.getTrades().get(0).getQuantity()).isEqualByComparingTo("5");
            assertThat(result.getTrades().get(1).getQuantity()).isEqualByComparingTo("2");
        }

        @Test
        void marketOrder_noLiquidity_canceled() {
            MatchResultDTO result = submit(marketBuy(10));
            assertThat(result.getTrades()).isEmpty();
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        void marketOrder_partialLiquidity_partiallyFilled() {
            submit(sellLimit(100, 3));
            MatchResultDTO result = submit(marketBuy(10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(result.getAffectedOrder().getFilledQuantity()).isEqualByComparingTo("3");
        }
    }

    // ==================== FOK ====================

    @Nested
    class FOKTests {

        @Test
        void fok_sufficientLiquidity_fills() {
            submit(sellLimit(100, 10));
            MatchResultDTO result = submit(fokBuy(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void fok_insufficientLiquidity_rejected() {
            submit(sellLimit(100, 5));
            MatchResultDTO result = submit(fokBuy(100, 10));

            assertThat(result.getTrades()).isEmpty();
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(result.getRejectReason()).isEqualTo("FOK_CANNOT_FILL");
            // maker 不受影响
            assertThat(engine.getOrderBook(SYMBOL).size()).isEqualTo(1);
        }

        @Test
        void fok_noLiquidity_rejected() {
            MatchResultDTO result = submit(fokBuy(100, 10));
            assertThat(result.getRejectReason()).isEqualTo("FOK_CANNOT_FILL");
        }
    }

    // ==================== IOC ====================

    @Nested
    class IOCTests {

        @Test
        void ioc_partialFill_remainingCanceled() {
            submit(sellLimit(100, 3));
            MatchResultDTO result = submit(iocBuy(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getFilledQuantity()).isEqualByComparingTo("3");
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(engine.getOrderBook(SYMBOL).size()).isZero();
        }

        @Test
        void ioc_fullFill() {
            submit(sellLimit(100, 10));
            MatchResultDTO result = submit(iocBuy(100, 10));

            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        void ioc_noMatch_canceled() {
            MatchResultDTO result = submit(iocBuy(100, 10));
            assertThat(result.getTrades()).isEmpty();
            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.CANCELED);
        }
    }

    // ==================== 止损单 ====================

    @Nested
    class StopOrderTests {

        @Test
        void stopMarketBuy_triggeredByPriceRise() {
            submit(sellLimit(105, 10));

            Order stopBuy = stopMarketBuy(105, 5);
            submit(stopBuy);
            assertThat(engine.getStopOrderBook(SYMBOL).size()).isEqualTo(1);

            // 提交一个卖单让价格升到 105 以触发
            submit(buyLimit(105, 2));

            // 止损单应被触发并撮合
            assertThat(engine.getStopOrderBook(SYMBOL).size()).isZero();
        }

        @Test
        void stopLimitSell_triggeredByPriceDrop() {
            submit(buyLimit(95, 10));

            Order stopSell = stopLimitSell(95, 94, 5);
            submit(stopSell);
            assertThat(engine.getStopOrderBook(SYMBOL).size()).isEqualTo(1);

            submit(sellLimit(95, 3));

            assertThat(engine.getStopOrderBook(SYMBOL).size()).isZero();
        }

        @Test
        void stopOrder_cancelBeforeTrigger() {
            Order stop = stopMarketBuy(110, 5);
            submit(stop);
            assertThat(engine.getStopOrderBook(SYMBOL).size()).isEqualTo(1);

            Optional<Boolean> result = engine.cancelOrder(SYMBOL, stop.getOrderId());
            assertThat(result).contains(true);
            assertThat(engine.getStopOrderBook(SYMBOL).size()).isZero();
        }
    }

    // ==================== 冰山单 ====================

    @Nested
    class IcebergTests {

        @Test
        void iceberg_depthShowsOnlyDisplayQuantity() {
            Order iceberg = icebergSell(100, 50, 10);
            submit(iceberg);

            MarketDepthDTO depth = engine.getMarketDepth(SYMBOL, 10);
            assertThat(depth.getAsks()).hasSize(1);
            assertThat(depth.getAsks().get(0).getQuantity()).isEqualByComparingTo("10");
        }

        @Test
        void iceberg_matchesFullQuantityAcrossClips() {
            Order iceberg = icebergSell(100, 30, 10);
            submit(iceberg);

            MatchResultDTO result = submit(buyLimit(100, 25));

            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(result.getAffectedOrder().getFilledQuantity()).isEqualByComparingTo("25");
            assertThat(engine.getOrderBook(SYMBOL).size()).isEqualTo(1);
        }

        @Test
        void iceberg_fullyConsumed() {
            Order iceberg = icebergSell(100, 20, 10);
            submit(iceberg);

            MatchResultDTO result = submit(buyLimit(100, 20));

            assertThat(result.getAffectedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
            assertThat(engine.getOrderBook(SYMBOL).size()).isZero();
        }
    }

    // ==================== 撤单 ====================

    @Nested
    class CancelOrderTests {

        @Test
        void cancel_existingOrder() {
            Order sell = sellLimit(100, 10);
            submit(sell);

            Optional<Boolean> result = engine.cancelOrder(SYMBOL, sell.getOrderId());
            assertThat(result).contains(true);
            assertThat(engine.getOrderBook(SYMBOL).size()).isZero();
        }

        @Test
        void cancel_nonExistentOrder() {
            Optional<Boolean> result = engine.cancelOrder(SYMBOL, 99999L);
            assertThat(result).isEmpty();
        }

        @Test
        void cancel_thenMatch_skipsCanceled() {
            Order sell1 = sellLimit(100, 5);
            Order sell2 = sellLimit(100, 5);
            submit(sell1);
            submit(sell2);

            engine.cancelOrder(SYMBOL, sell1.getOrderId());

            MatchResultDTO result = submit(buyLimit(100, 5));
            assertThat(result.getTrades()).hasSize(1);
            assertThat(result.getTrades().get(0).getMakerOrderId()).isEqualTo(sell2.getOrderId());
        }
    }

    // ==================== 盘口深度 ====================

    @Nested
    class DepthTests {

        @Test
        void depth_aggregatesQuantityByPrice() {
            submit(sellLimit(100, 5));
            submit(sellLimit(100, 3));
            submit(sellLimit(101, 7));

            MarketDepthDTO depth = engine.getMarketDepth(SYMBOL, 10);
            assertThat(depth.getAsks()).hasSize(2);
            assertThat(depth.getAsks().get(0).getPrice()).isEqualByComparingTo("100");
            assertThat(depth.getAsks().get(0).getQuantity()).isEqualByComparingTo("8");
            assertThat(depth.getAsks().get(1).getPrice()).isEqualByComparingTo("101");
        }

        @Test
        void depth_bidDescending() {
            submit(buyLimit(98, 5));
            submit(buyLimit(100, 5));
            submit(buyLimit(99, 5));

            MarketDepthDTO depth = engine.getMarketDepth(SYMBOL, 10);
            assertThat(depth.getBids()).hasSize(3);
            assertThat(depth.getBids().get(0).getPrice()).isEqualByComparingTo("100");
            assertThat(depth.getBids().get(1).getPrice()).isEqualByComparingTo("99");
            assertThat(depth.getBids().get(2).getPrice()).isEqualByComparingTo("98");
        }

        @Test
        void depth_respectsLevelLimit() {
            for (int i = 1; i <= 20; i++) {
                submit(sellLimit(100 + i, 1));
            }
            MarketDepthDTO depth = engine.getMarketDepth(SYMBOL, 5);
            assertThat(depth.getAsks()).hasSize(5);
        }

        @Test
        void depth_uninitializedSymbol() {
            MarketDepthDTO depth = engine.getMarketDepth("UNKNOWN", 10);
            assertThat(depth.getBids()).isEmpty();
            assertThat(depth.getAsks()).isEmpty();
        }
    }

    // ==================== 工厂方法 ====================

    private MatchResultDTO submit(Order order) {
        return engine.submitOrder(order);
    }

    private Order buyLimit(double price, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(1L)
                .symbol(SYMBOL)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order sellLimit(double price, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(2L)
                .symbol(SYMBOL)
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order marketBuy(double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(1L)
                .symbol(SYMBOL)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .timeInForce(TimeInForce.GTC)
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order marketSell(double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(2L)
                .symbol(SYMBOL)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .timeInForce(TimeInForce.GTC)
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order fokBuy(double price, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(1L)
                .symbol(SYMBOL)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.FOK)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order iocBuy(double price, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(1L)
                .symbol(SYMBOL)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.IOC)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order stopMarketBuy(double triggerPrice, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(1L)
                .symbol(SYMBOL)
                .side(OrderSide.BUY)
                .type(OrderType.STOP_MARKET)
                .triggerPrice(BigDecimal.valueOf(triggerPrice))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order stopLimitSell(double triggerPrice, double limitPrice, double qty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(2L)
                .symbol(SYMBOL)
                .side(OrderSide.SELL)
                .type(OrderType.STOP_LIMIT)
                .triggerPrice(BigDecimal.valueOf(triggerPrice))
                .price(BigDecimal.valueOf(limitPrice))
                .quantity(BigDecimal.valueOf(qty))
                .sequenceTime(System.nanoTime())
                .build();
    }

    private Order icebergSell(double price, double totalQty, double displayQty) {
        return Order.builder()
                .orderId(orderIdSeq.incrementAndGet())
                .userId(2L)
                .symbol(SYMBOL)
                .side(OrderSide.SELL)
                .type(OrderType.ICEBERG)
                .timeInForce(TimeInForce.GTC)
                .price(BigDecimal.valueOf(price))
                .quantity(BigDecimal.valueOf(totalQty))
                .displayQuantity(BigDecimal.valueOf(displayQty))
                .sequenceTime(System.nanoTime())
                .build();
    }
}
