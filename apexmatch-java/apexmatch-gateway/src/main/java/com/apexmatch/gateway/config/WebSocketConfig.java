package com.apexmatch.gateway.config;

import com.apexmatch.gateway.websocket.MarketDataWebSocketHandler;
import com.apexmatch.gateway.websocket.WebSocketSessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 *
 * @author luka
 * @since 2025-03-26
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Bean
    public WebSocketSessionManager webSocketSessionManager() {
        return new WebSocketSessionManager();
    }

    @Bean
    public MarketDataWebSocketHandler marketDataWebSocketHandler(WebSocketSessionManager manager) {
        return new MarketDataWebSocketHandler(manager);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataWebSocketHandler(webSocketSessionManager()), "/ws/market")
                .setAllowedOrigins("*");
    }
}
