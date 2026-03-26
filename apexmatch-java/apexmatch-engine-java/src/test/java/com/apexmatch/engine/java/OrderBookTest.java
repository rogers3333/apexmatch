package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.engine.api.dto.DepthLevelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OrderBook} 单元测试。
 *
 * @author luka
 * @since 2025-03-26
 */
class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook("BTC-USDT");
    }

    @Test
    void addAndQueryOrder() {
        Order order = makeBuyLimit(1L, 100, 10);
        book.addOrder(order);

        assertThat(book.size()).isEqualTo(1);
        assertThat(book.containsOrder(1L)).isTrue();
        assertThat(book.getOrder(1L)).isSameAs(order);
    }

    @Test
    void cancelOrder_lazily() {
        Order order = makeBuyLimit(1L, 100, 10);
        book.addOrder(order);

        Order canceled = book.cancelOrder(1L);
        assertThat(canceled).isSameAs(order);
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(book.size()).isZero();
        assertThat(book.containsOrder(1L)).isFalse();
    }

    @Test
    void cancelOrder_nonExistent_returnsNull() {
        assertThat(book.cancelOrder(999L)).isNull();
    }

    @Test
    void bidDepth_descendingPrice() {
        book.addOrder(makeBuyLimit(1L, 98, 5));
        book.addOrder(makeBuyLimit(2L, 100, 3));
        book.addOrder(makeBuyLimit(3L, 99, 7));

        List<DepthLevelDTO> depth = book.getBidDepth(10);
        assertThat(depth).hasSize(3);
        assertThat(depth.get(0).getPrice()).isEqualByComparingTo("100");
        assertThat(depth.get(1).getPrice()).isEqualByComparingTo("99");
        assertThat(depth.get(2).getPrice()).isEqualByComparingTo("98");
    }

    @Test
    void askDepth_ascendingPrice() {
        book.addOrder(makeSellLimit(1L, 102, 5));
        book.addOrder(makeSellLimit(2L, 100, 3));
        book.addOrder(makeSellLimit(3L, 101, 7));

        List<DepthLevelDTO> depth = book.getAskDepth(10);
        assertThat(depth).hasSize(3);
        assertThat(depth.get(0).getPrice()).isEqualByComparingTo("100");
        assertThat(depth.get(1).getPrice()).isEqualByComparingTo("101");
        assertThat(depth.get(2).getPrice()).isEqualByComparingTo("102");
    }

    @Test
    void depth_aggregatesSamePrice() {
        book.addOrder(makeSellLimit(1L, 100, 5));
        book.addOrder(makeSellLimit(2L, 100, 3));

        List<DepthLevelDTO> depth = book.getAskDepth(10);
        assertThat(depth).hasSize(1);
        assertThat(depth.get(0).getQuantity()).isEqualByComparingTo("8");
    }

    @Test
    void depth_skipsCanceled() {
        book.addOrder(makeSellLimit(1L, 100, 5));
        book.addOrder(makeSellLimit(2L, 100, 3));
        book.cancelOrder(1L);

        List<DepthLevelDTO> depth = book.getAskDepth(10);
        assertThat(depth).hasSize(1);
        assertThat(depth.get(0).getQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void allActiveOrders() {
        book.addOrder(makeBuyLimit(1L, 100, 5));
        book.addOrder(makeSellLimit(2L, 110, 5));

        assertThat(book.allActiveOrders()).hasSize(2);
    }

    @Test
    void remaining_and_clipRemaining() {
        Order normal = makeSellLimit(1L, 100, 10);
        normal.setFilledQuantity(BigDecimal.valueOf(3));

        assertThat(OrderBook.remaining(normal)).isEqualByComparingTo("7");
        assertThat(OrderBook.clipRemaining(normal)).isEqualByComparingTo("7");

        Order iceberg = Order.builder()
                .orderId(2L)
                .symbol("BTC-USDT")
                .side(OrderSide.SELL)
                .type(OrderType.ICEBERG)
                .price(BigDecimal.valueOf(100))
                .quantity(BigDecimal.valueOf(50))
                .displayQuantity(BigDecimal.valueOf(10))
                .build();
        assertThat(OrderBook.clipRemaining(iceberg)).isEqualByComparingTo("10");

        iceberg.setFilledQuantity(BigDecimal.valueOf(7));
        assertThat(OrderBook.clipRemaining(iceberg)).isEqualByComparingTo("3");

        iceberg.setFilledQuantity(BigDecimal.valueOf(10));
        assertThat(OrderBook.clipRemaining(iceberg)).isEqualByComparingTo("10");

        iceberg.setFilledQuantity(BigDecimal.valueOf(45));
        assertThat(OrderBook.clipRemaining(iceberg)).isEqualByComparingTo("5");
    }

    // ==================== 工厂 ====================

    private static Order makeBuyLimit(long id, double price, double qty) {
        return Order.builder()
                .orderId(id).userId(1L).symbol("BTC-USDT")
                .side(OrderSide.BUY).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(BigDecimal.valueOf(price)).quantity(BigDecimal.valueOf(qty))
                .build();
    }

    private static Order makeSellLimit(long id, double price, double qty) {
        return Order.builder()
                .orderId(id).userId(2L).symbol("BTC-USDT")
                .side(OrderSide.SELL).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(BigDecimal.valueOf(price)).quantity(BigDecimal.valueOf(qty))
                .build();
    }
}
