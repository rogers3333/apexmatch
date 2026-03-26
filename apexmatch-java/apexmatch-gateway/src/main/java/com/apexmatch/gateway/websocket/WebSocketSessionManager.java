package com.apexmatch.gateway.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话管理器。
 * 按主题（交易对）管理订阅关系，支持按用户 ID 查找私有通道。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class WebSocketSessionManager {

    /** 主题（如 "depth:BTC-USDT"） → 订阅该主题的会话集合 */
    private final Map<String, Set<WebSocketSession>> topicSubscribers = new ConcurrentHashMap<>();

    /** 用户 ID → 私有通道会话 */
    private final Map<Long, WebSocketSession> privateSessions = new ConcurrentHashMap<>();

    /** sessionId → session */
    private final Map<String, WebSocketSession> allSessions = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session) {
        allSessions.put(session.getId(), session);
    }

    public void removeSession(WebSocketSession session) {
        allSessions.remove(session.getId());
        topicSubscribers.values().forEach(s -> s.remove(session));
        privateSessions.values().removeIf(s -> s.getId().equals(session.getId()));
    }

    public void subscribe(WebSocketSession session, String topic) {
        topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("订阅 topic={} sessionId={}", topic, session.getId());
    }

    public void unsubscribe(WebSocketSession session, String topic) {
        Set<WebSocketSession> subs = topicSubscribers.get(topic);
        if (subs != null) subs.remove(session);
    }

    public void bindUser(long userId, WebSocketSession session) {
        privateSessions.put(userId, session);
    }

    /**
     * 向所有订阅某主题的会话推送消息。
     */
    public void broadcast(String topic, String payload) {
        Set<WebSocketSession> subs = topicSubscribers.get(topic);
        if (subs == null || subs.isEmpty()) return;

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : subs) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("推送失败 sessionId={}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 向指定用户推送私有消息。
     */
    public void sendToUser(long userId, String payload) {
        WebSocketSession session = privateSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.warn("私有推送失败 userId={}: {}", userId, e.getMessage());
            }
        }
    }

    public int sessionCount() {
        return allSessions.size();
    }

    public int subscriberCount(String topic) {
        Set<WebSocketSession> subs = topicSubscribers.get(topic);
        return subs != null ? subs.size() : 0;
    }
}
