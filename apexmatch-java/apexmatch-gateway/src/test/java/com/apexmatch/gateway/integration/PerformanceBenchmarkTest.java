package com.apexmatch.gateway.integration;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.engine.java.JavaMatchingEngine;
import com.apexmatch.gateway.disruptor.OrderDisruptorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * 性能压测：单交易对 TPS、延迟百分位、Disruptor 吞吐。
 *
 * @author luka
 * @since 2025-03-26
 */
class PerformanceBenchmarkTest {

    private static final String SYM = "BTC-USDT";
    private JavaMatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JavaMatchingEngine();
        engine.init(SYM, null);
    }

    @Test
    @DisplayName("压测：纯插入（无撮合）10 万笔，计算 TPS")
    void insertOnlyThroughput() {
        int count = 100_000;
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            Order order = Order.builder()
                    .orderId((long) i).userId(1L).symbol(SYM)
                    .side(OrderSide.SELL).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                    .price(new BigDecimal("50000").add(BigDecimal.valueOf(i)))
                    .quantity(BigDecimal.ONE)
                    .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                    .build();
            engine.submitOrder(order);
        }

        long elapsed = System.nanoTime() - start;
        double tps = count / (elapsed / 1_000_000_000.0);
        System.out.printf("[插入压测] %d 笔, 耗时 %.2f ms, TPS = %.0f%n",
                count, elapsed / 1_000_000.0, tps);

        assertThat(tps).isGreaterThan(10_000);
    }

    @Test
    @DisplayName("压测：完全撮合 5 万对买卖单，计算 TPS")
    void matchThroughput() {
        int pairs = 50_000;

        for (int i = 0; i < pairs; i++) {
            Order sell = Order.builder()
                    .orderId((long) (i * 2)).userId(1L).symbol(SYM)
                    .side(OrderSide.SELL).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                    .price(new BigDecimal("50000"))
                    .quantity(BigDecimal.ONE)
                    .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                    .build();
            engine.submitOrder(sell);
        }

        long start = System.nanoTime();
        int matched = 0;

        for (int i = 0; i < pairs; i++) {
            Order buy = Order.builder()
                    .orderId((long) (i * 2 + 1)).userId(2L).symbol(SYM)
                    .side(OrderSide.BUY).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                    .price(new BigDecimal("50000"))
                    .quantity(BigDecimal.ONE)
                    .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                    .build();
            MatchResultDTO r = engine.submitOrder(buy);
            if (!r.getTrades().isEmpty()) matched++;
        }

        long elapsed = System.nanoTime() - start;
        double tps = pairs / (elapsed / 1_000_000_000.0);
        System.out.printf("[撮合压测] %d 对, 成交 %d, 耗时 %.2f ms, TPS = %.0f%n",
                pairs, matched, elapsed / 1_000_000.0, tps);

        assertThat(matched).isEqualTo(pairs);
        assertThat(tps).isGreaterThan(10_000);
    }

    @Test
    @DisplayName("压测：延迟百分位（P50/P95/P99）")
    void latencyPercentiles() {
        for (int i = 0; i < 10_000; i++) {
            Order sell = Order.builder()
                    .orderId((long) (i * 2)).userId(1L).symbol(SYM)
                    .side(OrderSide.SELL).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                    .price(new BigDecimal("50000"))
                    .quantity(BigDecimal.ONE)
                    .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                    .build();
            engine.submitOrder(sell);
        }

        long[] latencies = new long[10_000];

        for (int i = 0; i < 10_000; i++) {
            Order buy = Order.builder()
                    .orderId((long) (i * 2 + 1)).userId(2L).symbol(SYM)
                    .side(OrderSide.BUY).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                    .price(new BigDecimal("50000"))
                    .quantity(BigDecimal.ONE)
                    .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                    .build();
            long t0 = System.nanoTime();
            engine.submitOrder(buy);
            latencies[i] = System.nanoTime() - t0;
        }

        Arrays.sort(latencies);
        double p50 = latencies[(int) (latencies.length * 0.50)] / 1_000.0;
        double p95 = latencies[(int) (latencies.length * 0.95)] / 1_000.0;
        double p99 = latencies[(int) (latencies.length * 0.99)] / 1_000.0;

        System.out.printf("[延迟百分位] P50=%.2f μs, P95=%.2f μs, P99=%.2f μs%n", p50, p95, p99);

        assertThat(p99).isLessThan(5000);
    }

    @Test
    @DisplayName("压测：Disruptor 多线程并发提交")
    void disruptorConcurrentSubmit() throws Exception {
        JavaMatchingEngine eng = new JavaMatchingEngine();
        eng.init(SYM, null);
        OrderDisruptorService disruptor = new OrderDisruptorService(eng);
        disruptor.start();

        int threads = 4;
        int perThread = 5_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicLong totalOrders = new AtomicLong(0);
        AtomicLong orderId = new AtomicLong(1);

        long start = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        Order order = Order.builder()
                                .orderId(orderId.getAndIncrement()).userId(1L).symbol(SYM)
                                .side(i % 2 == 0 ? OrderSide.SELL : OrderSide.BUY)
                                .type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                                .price(new BigDecimal("50000"))
                                .quantity(BigDecimal.ONE)
                                .filledQuantity(BigDecimal.ZERO).status(OrderStatus.NEW)
                                .build();
                        disruptor.submitOrder(order, 10_000);
                        totalOrders.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        double tps = totalOrders.get() / (elapsed / 1_000_000_000.0);

        System.out.printf("[Disruptor 并发压测] 线程=%d, 总订单=%d, 耗时 %.2f ms, TPS = %.0f%n",
                threads, totalOrders.get(), elapsed / 1_000_000.0, tps);

        assertThat(totalOrders.get()).isEqualTo(threads * perThread);

        executor.shutdown();
        disruptor.shutdown();
    }
}
