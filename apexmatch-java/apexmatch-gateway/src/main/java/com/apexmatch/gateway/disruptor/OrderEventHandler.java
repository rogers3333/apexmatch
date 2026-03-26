package com.apexmatch.gateway.disruptor;

import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.lmax.disruptor.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disruptor 消费者：从环形缓冲区取出订单事件并交给撮合引擎处理。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class OrderEventHandler implements EventHandler<OrderEvent> {

    private final MatchingEngine engine;

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            switch (event.getEventType()) {
                case PLACE_ORDER -> {
                    MatchResultDTO result = engine.submitOrder(event.getOrder());
                    event.setMatchResult(result);
                    event.setSuccess(true);
                }
                case CANCEL_ORDER -> {
                    engine.cancelOrder(event.getSymbol(), event.getOrderId());
                    event.setSuccess(true);
                }
            }
        } catch (Exception e) {
            log.error("订单处理异常 seq={}: {}", sequence, e.getMessage());
            event.setSuccess(false);
            event.setErrorMessage(e.getMessage());
        } finally {
            if (event.getLatch() != null) {
                event.getLatch().countDown();
            }
        }
    }
}
