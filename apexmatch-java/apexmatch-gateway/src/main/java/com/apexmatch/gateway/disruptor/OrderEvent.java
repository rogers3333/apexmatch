package com.apexmatch.gateway.disruptor;

import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import lombok.Data;

import java.util.concurrent.CountDownLatch;

/**
 * Disruptor 环形缓冲区事件：承载一个订单请求的全生命周期数据。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
public class OrderEvent {

    public enum EventType {
        PLACE_ORDER, CANCEL_ORDER
    }

    private EventType eventType;
    private Order order;
    private String symbol;
    private Long orderId;
    private long userId;

    private MatchResultDTO matchResult;
    private boolean success;
    private String errorMessage;

    /** 同步等待用，调用方阻塞直到处理完成 */
    private CountDownLatch latch;

    public void clear() {
        eventType = null;
        order = null;
        symbol = null;
        orderId = null;
        userId = 0;
        matchResult = null;
        success = false;
        errorMessage = null;
        latch = null;
    }
}
