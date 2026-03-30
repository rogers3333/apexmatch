package com.apexmatch.gateway.disruptor;

import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Disruptor 订单队列服务：提供自然背压，保证撮合引擎单线程无锁处理。
 *
 * 性能优化配置：
 * - Buffer Size: 256K（高频交易场景）
 * - WaitStrategy: BusySpinWaitStrategy（最低延迟，适合专用 CPU 核心）
 * - ProducerType: MULTI（支持多生产者并发提交）
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class OrderDisruptorService {

    /**
     * 环形缓冲区大小：256K
     * 高频交易场景下，64K 可能不足，增大到 256K 减少背压阻塞
     * 必须是 2 的幂次方
     */
    private static final int BUFFER_SIZE = 1024 * 256;

    private final MatchingEngine engine;
    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    public OrderDisruptorService(MatchingEngine engine) {
        this.engine = engine;
    }

    @PostConstruct
    public void start() {
        // BusySpinWaitStrategy: 最低延迟等待策略
        // 消费者线程会持续自旋等待新事件，CPU 占用高但延迟最低
        // 适合专用 CPU 核心的高频交易场景
        disruptor = new Disruptor<>(
                OrderEvent::new,
                BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BusySpinWaitStrategy()
        );
        disruptor.handleEventsWith(new OrderEventHandler(engine));
        ringBuffer = disruptor.start();
        log.info("Disruptor 订单队列启动 bufferSize={} waitStrategy=BusySpinWaitStrategy", BUFFER_SIZE);
    }

    @PreDestroy
    public void shutdown() {
        if (disruptor != null) {
            disruptor.shutdown();
            log.info("Disruptor 订单队列关闭");
        }
    }

    /**
     * 提交下单请求到环形缓冲区，阻塞等待结果。
     */
    public MatchResultDTO submitOrder(Order order, long timeoutMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long seq = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(seq);
            event.clear();
            event.setEventType(OrderEvent.EventType.PLACE_ORDER);
            event.setOrder(order);
            event.setLatch(latch);
        } finally {
            ringBuffer.publish(seq);
        }

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("订单处理超时");
        }

        OrderEvent event = ringBuffer.get(seq);
        if (!event.isSuccess()) {
            throw new RuntimeException(event.getErrorMessage());
        }
        return event.getMatchResult();
    }

    /**
     * 提交撤单请求到环形缓冲区。
     */
    public void cancelOrder(String symbol, long orderId, long timeoutMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        long seq = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(seq);
            event.clear();
            event.setEventType(OrderEvent.EventType.CANCEL_ORDER);
            event.setSymbol(symbol);
            event.setOrderId(orderId);
            event.setLatch(latch);
        } finally {
            ringBuffer.publish(seq);
        }

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("撤单处理超时");
        }
    }

    public long remainingCapacity() {
        return ringBuffer != null ? ringBuffer.remainingCapacity() : 0;
    }
}
