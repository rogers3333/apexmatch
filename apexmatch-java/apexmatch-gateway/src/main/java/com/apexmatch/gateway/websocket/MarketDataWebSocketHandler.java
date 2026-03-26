package com.apexmatch.gateway.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 行情 WebSocket 处理器。
 * <p>
 * 客户端通过发送 JSON 消息来订阅/退订主题：
 * <pre>
 * {"action":"subscribe","topic":"depth:BTC-USDT"}
 * {"action":"unsubscribe","topic":"depth:BTC-USDT"}
 * </pre>
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.addSession(session);
        log.info("WebSocket 连接建立 sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        String action = json.path("action").asText();
        String topic = json.path("topic").asText();

        switch (action) {
            case "subscribe" -> {
                sessionManager.subscribe(session, topic);
                session.sendMessage(new TextMessage(
                        "{\"status\":\"subscribed\",\"topic\":\"" + topic + "\"}"));
            }
            case "unsubscribe" -> {
                sessionManager.unsubscribe(session, topic);
                session.sendMessage(new TextMessage(
                        "{\"status\":\"unsubscribed\",\"topic\":\"" + topic + "\"}"));
            }
            default -> session.sendMessage(new TextMessage(
                    "{\"error\":\"unknown action: " + action + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);
        log.info("WebSocket 连接关闭 sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输异常 sessionId={}: {}", session.getId(), exception.getMessage());
        sessionManager.removeSession(session);
    }
}
