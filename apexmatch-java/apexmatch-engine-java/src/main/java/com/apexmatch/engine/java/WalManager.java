package com.apexmatch.engine.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 简易 WAL（Write-Ahead Log）管理器。
 * <p>
 * 每条记录格式：<code>[4 字节总长度][1 字节类型][数据]</code>。
 * 适用于开发阶段；生产可替换为 <code>O_DIRECT</code> / mmap 方案。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
public class WalManager {

    private static final Logger log = LoggerFactory.getLogger(WalManager.class);

    private static final byte TYPE_SUBMIT = 0;
    private static final byte TYPE_CANCEL = 1;

    private final Path walFile;
    private DataOutputStream dos;

    /**
     * @param walDirectory WAL 文件存放目录
     * @param symbol       交易对，文件名为 <code>{symbol}.wal</code>
     */
    public WalManager(String walDirectory, String symbol) {
        this.walFile = Path.of(walDirectory, symbol + ".wal");
        try {
            Files.createDirectories(walFile.getParent());
            this.dos = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(walFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)));
        } catch (IOException e) {
            log.error("WAL 初始化失败: {}", walFile, e);
        }
    }

    /**
     * 追加一条 SUBMIT 记录。
     */
    public synchronized void appendSubmit(com.apexmatch.common.entity.Order order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            DataOutputStream tmp = new DataOutputStream(baos);
            OrderSerializer.write(tmp, order);
            tmp.flush();
            byte[] data = baos.toByteArray();

            dos.writeInt(data.length + 1);
            dos.writeByte(TYPE_SUBMIT);
            dos.write(data);
            dos.flush();
        } catch (IOException e) {
            log.error("WAL 写 SUBMIT 失败: orderId={}", order.getOrderId(), e);
        }
    }

    /**
     * 追加一条 CANCEL 记录。
     */
    public synchronized void appendCancel(String symbol, long orderId) {
        try {
            int dataLen = 8 + 2 + symbol.length();
            dos.writeInt(dataLen + 1);
            dos.writeByte(TYPE_CANCEL);
            dos.writeLong(orderId);
            dos.writeUTF(symbol);
            dos.flush();
        } catch (IOException e) {
            log.error("WAL 写 CANCEL 失败: orderId={}", orderId, e);
        }
    }

    /**
     * 从头读取所有条目（用于崩溃恢复）。
     */
    public List<WalEntry> readAll() {
        List<WalEntry> entries = new ArrayList<>();
        if (!Files.exists(walFile)) return entries;

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(walFile)))) {
            while (dis.available() > 0) {
                int len = dis.readInt();
                byte type = dis.readByte();
                if (type == TYPE_SUBMIT) {
                    entries.add(WalEntry.ofSubmit(OrderSerializer.read(dis)));
                } else if (type == TYPE_CANCEL) {
                    long orderId = dis.readLong();
                    String symbol = dis.readUTF();
                    entries.add(WalEntry.ofCancel(symbol, orderId));
                } else {
                    dis.skipBytes(len - 1);
                }
            }
        } catch (IOException e) {
            log.warn("WAL 读取中断（可能是文件末尾不完整）: {}", walFile, e);
        }
        return entries;
    }

    /**
     * 截断 WAL（在快照完成后调用）。
     */
    public synchronized void truncate() {
        try {
            close();
            Files.deleteIfExists(walFile);
            this.dos = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(walFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)));
        } catch (IOException e) {
            log.error("WAL 截断失败: {}", walFile, e);
        }
    }

    public synchronized void close() {
        try {
            if (dos != null) dos.close();
        } catch (IOException e) {
            log.error("WAL 关闭失败", e);
        }
    }
}
