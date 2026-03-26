package com.apexmatch.gateway.disruptor;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.engine.java.JavaMatchingEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class OrderDisruptorServiceTest {

    private OrderDisruptorService service;

    @BeforeEach
    void setUp() {
        JavaMatchingEngine engine = new JavaMatchingEngine();
        engine.init("BTC-USDT", null);
        service = new OrderDisruptorService(engine);
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void submitOrderViaDisruptor() throws InterruptedException {
        Order order = makeOrder(1L, OrderSide.BUY, "50000", "1");
        MatchResultDTO result = service.submitOrder(order, 5000);

        assertThat(result).isNotNull();
        assertThat(result.getAffectedOrder().getOrderId()).isEqualTo(1L);
    }

    @Test
    void matchOrdersViaDisruptor() throws InterruptedException {
        Order sell = makeOrder(1L, OrderSide.SELL, "50000", "1");
        service.submitOrder(sell, 5000);

        Order buy = makeOrder(2L, OrderSide.BUY, "50000", "1");
        MatchResultDTO result = service.submitOrder(buy, 5000);

        assertThat(result.getTrades()).hasSize(1);
    }

    @Test
    void cancelOrderViaDisruptor() throws InterruptedException {
        Order sell = makeOrder(1L, OrderSide.SELL, "50000", "1");
        service.submitOrder(sell, 5000);

        service.cancelOrder("BTC-USDT", 1L, 5000);
    }

    @Test
    void remainingCapacityReported() {
        assertThat(service.remainingCapacity()).isGreaterThan(0);
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
