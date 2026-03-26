package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SnapshotManager} 保存与恢复测试。
 *
 * @author luka
 * @since 2025-03-26
 */
class SnapshotManagerTest {

    @TempDir
    Path tmpDir;

    @Test
    void saveAndLoad() {
        OrderBook book = new OrderBook("BTC-USDT");
        book.addOrder(Order.builder()
                .orderId(1L).userId(1L).symbol("BTC-USDT")
                .side(OrderSide.BUY).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000")).quantity(new BigDecimal("2"))
                .build());
        book.addOrder(Order.builder()
                .orderId(2L).userId(2L).symbol("BTC-USDT")
                .side(OrderSide.SELL).type(OrderType.LIMIT).timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("51000")).quantity(new BigDecimal("3"))
                .build());

        StopOrderBook stopBook = new StopOrderBook();
        stopBook.addStopOrder(Order.builder()
                .orderId(3L).userId(1L).symbol("BTC-USDT")
                .side(OrderSide.BUY).type(OrderType.STOP_MARKET)
                .triggerPrice(new BigDecimal("52000")).quantity(new BigDecimal("1"))
                .build());

        SnapshotManager snap = new SnapshotManager(tmpDir.toString(), "BTC-USDT");
        snap.save(book, stopBook);

        List<Order> restored = snap.loadLatest();
        assertThat(restored).hasSize(3);
        assertThat(restored.stream().map(Order::getOrderId).toList())
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void loadLatest_noSnapshot_returnsEmpty() {
        SnapshotManager snap = new SnapshotManager(tmpDir.toString(), "BTC-USDT");
        assertThat(snap.loadLatest()).isEmpty();
    }
}
