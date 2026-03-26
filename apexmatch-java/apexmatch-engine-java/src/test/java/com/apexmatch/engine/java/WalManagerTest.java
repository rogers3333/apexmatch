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
 * {@link WalManager} 写入与恢复测试。
 *
 * @author luka
 * @since 2025-03-26
 */
class WalManagerTest {

    @TempDir
    Path tmpDir;

    @Test
    void submitAndRecover() {
        WalManager wal = new WalManager(tmpDir.toString(), "BTC-USDT");
        Order order = Order.builder()
                .orderId(1L)
                .clientOrderId("client-001")
                .userId(100L)
                .symbol("BTC-USDT")
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GTC)
                .price(new BigDecimal("50000.50"))
                .quantity(new BigDecimal("1.5"))
                .triggerPrice(null)
                .sequenceTime(System.currentTimeMillis())
                .build();

        wal.appendSubmit(order);
        wal.close();

        WalManager wal2 = new WalManager(tmpDir.toString(), "BTC-USDT");
        List<WalEntry> entries = wal2.readAll();
        wal2.close();

        assertThat(entries).hasSize(1);
        WalEntry entry = entries.get(0);
        assertThat(entry.getType()).isEqualTo(WalEntry.Type.SUBMIT);
        assertThat(entry.getOrder().getOrderId()).isEqualTo(1L);
        assertThat(entry.getOrder().getPrice()).isEqualByComparingTo("50000.50");
        assertThat(entry.getOrder().getQuantity()).isEqualByComparingTo("1.5");
        assertThat(entry.getOrder().getClientOrderId()).isEqualTo("client-001");
    }

    @Test
    void cancelAndRecover() {
        WalManager wal = new WalManager(tmpDir.toString(), "BTC-USDT");
        wal.appendCancel("BTC-USDT", 42L);
        wal.close();

        List<WalEntry> entries = new WalManager(tmpDir.toString(), "BTC-USDT").readAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getType()).isEqualTo(WalEntry.Type.CANCEL);
        assertThat(entries.get(0).getOrderId()).isEqualTo(42L);
        assertThat(entries.get(0).getSymbol()).isEqualTo("BTC-USDT");
    }

    @Test
    void multipleEntries() {
        WalManager wal = new WalManager(tmpDir.toString(), "ETH-USDT");
        for (int i = 1; i <= 5; i++) {
            wal.appendSubmit(Order.builder()
                    .orderId((long) i)
                    .userId(1L)
                    .symbol("ETH-USDT")
                    .side(OrderSide.SELL)
                    .type(OrderType.MARKET)
                    .quantity(BigDecimal.TEN)
                    .build());
        }
        wal.appendCancel("ETH-USDT", 3L);
        wal.close();

        List<WalEntry> entries = new WalManager(tmpDir.toString(), "ETH-USDT").readAll();
        assertThat(entries).hasSize(6);
        assertThat(entries.get(5).getType()).isEqualTo(WalEntry.Type.CANCEL);
    }

    @Test
    void truncate_clearsWal() {
        WalManager wal = new WalManager(tmpDir.toString(), "BTC-USDT");
        wal.appendSubmit(Order.builder()
                .orderId(1L).userId(1L).symbol("BTC-USDT")
                .side(OrderSide.BUY).type(OrderType.LIMIT)
                .price(BigDecimal.ONE).quantity(BigDecimal.ONE)
                .build());
        wal.truncate();

        List<WalEntry> entries = wal.readAll();
        assertThat(entries).isEmpty();
        wal.close();
    }
}
