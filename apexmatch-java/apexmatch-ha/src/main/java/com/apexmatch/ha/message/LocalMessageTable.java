package com.apexmatch.ha.message;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 本地消息表内存实现。
 * 生产环境替换为 MySQL 本地事务 + 定时扫描。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class LocalMessageTable {

    private static final int MAX_RETRY = 5;

    private final Map<Long, LocalMessage> messages = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    /** 写入一条待发送消息 */
    public LocalMessage save(String topic, String payload) {
        LocalMessage msg = LocalMessage.builder()
                .messageId(idGen.getAndIncrement())
                .topic(topic)
                .payload(payload)
                .status(LocalMessage.Status.PENDING)
                .retryCount(0)
                .createdTime(System.currentTimeMillis())
                .updatedTime(System.currentTimeMillis())
                .build();
        messages.put(msg.getMessageId(), msg);
        log.debug("消息入表 id={} topic={}", msg.getMessageId(), topic);
        return msg;
    }

    /** 扫描待发送消息并投递 */
    public int scan(Consumer<LocalMessage> sender) {
        int sent = 0;
        for (LocalMessage msg : messages.values()) {
            if (msg.getStatus() != LocalMessage.Status.PENDING) continue;
            if (msg.getRetryCount() >= MAX_RETRY) {
                msg.setStatus(LocalMessage.Status.FAILED);
                msg.setUpdatedTime(System.currentTimeMillis());
                log.warn("消息超过最大重试次数 id={}", msg.getMessageId());
                continue;
            }
            try {
                sender.accept(msg);
                msg.setStatus(LocalMessage.Status.SENT);
                msg.setUpdatedTime(System.currentTimeMillis());
                sent++;
            } catch (Exception e) {
                msg.setRetryCount(msg.getRetryCount() + 1);
                msg.setUpdatedTime(System.currentTimeMillis());
                log.warn("消息投递失败 id={} retry={}: {}", msg.getMessageId(), msg.getRetryCount(), e.getMessage());
            }
        }
        return sent;
    }

    /** 确认消息已被消费 */
    public void confirm(long messageId) {
        LocalMessage msg = messages.get(messageId);
        if (msg != null) {
            msg.setStatus(LocalMessage.Status.CONFIRMED);
            msg.setUpdatedTime(System.currentTimeMillis());
        }
    }

    public List<LocalMessage> getPending() {
        List<LocalMessage> result = new ArrayList<>();
        for (LocalMessage msg : messages.values()) {
            if (msg.getStatus() == LocalMessage.Status.PENDING) {
                result.add(msg);
            }
        }
        return result;
    }

    public LocalMessage get(long messageId) {
        return messages.get(messageId);
    }

    public int size() {
        return messages.size();
    }
}
