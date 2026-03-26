package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 快照管理器：定期将订单簿 + 止损簿完整状态持久化。
 * <p>
 * 文件格式：<code>[4 字节订单数][N × 序列化 Order]</code>。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
public class SnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);

    private final Path snapshotDir;
    private final String symbol;

    public SnapshotManager(String snapshotDirectory, String symbol) {
        this.snapshotDir = Path.of(snapshotDirectory);
        this.symbol = symbol;
        try {
            Files.createDirectories(snapshotDir);
        } catch (IOException e) {
            log.error("快照目录创建失败: {}", snapshotDir, e);
        }
    }

    /**
     * 保存快照：将订单簿与止损簿中的所有活跃订单写入文件。
     */
    public void save(OrderBook orderBook, StopOrderBook stopOrderBook) {
        Path file = snapshotDir.resolve(symbol + "_" + System.currentTimeMillis() + ".snap");
        List<Order> allOrders = new ArrayList<>(orderBook.allActiveOrders());
        allOrders.addAll(stopOrderBook.allStopOrders());

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            dos.writeInt(allOrders.size());
            for (Order o : allOrders) {
                OrderSerializer.write(dos, o);
            }
            log.info("快照保存成功: file={}, orders={}", file, allOrders.size());
        } catch (IOException e) {
            log.error("快照保存失败: {}", file, e);
        }
    }

    /**
     * 加载最近一次快照。
     *
     * @return 恢复的订单列表；无快照则返回空列表
     */
    public List<Order> loadLatest() {
        try (var files = Files.list(snapshotDir)) {
            Optional<Path> latest = files
                    .filter(p -> p.getFileName().toString().startsWith(symbol + "_")
                            && p.getFileName().toString().endsWith(".snap"))
                    .max(java.util.Comparator.comparing(p -> p.getFileName().toString()));

            if (latest.isEmpty()) {
                return Collections.emptyList();
            }

            return loadFrom(latest.get());
        } catch (IOException e) {
            log.warn("快照加载失败: dir={}", snapshotDir, e);
            return Collections.emptyList();
        }
    }

    List<Order> loadFrom(Path file) throws IOException {
        List<Order> orders = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                orders.add(OrderSerializer.read(dis));
            }
        }
        log.info("快照加载成功: file={}, orders={}", file, orders.size());
        return orders;
    }
}
