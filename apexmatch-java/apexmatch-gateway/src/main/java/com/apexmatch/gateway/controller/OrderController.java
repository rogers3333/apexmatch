package com.apexmatch.gateway.controller;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.common.util.SnowflakeIdGenerator;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import com.apexmatch.gateway.disruptor.OrderDisruptorService;
import com.apexmatch.gateway.dto.ApiResponse;
import com.apexmatch.gateway.dto.CancelOrderRequest;
import com.apexmatch.gateway.dto.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 订单 REST API。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "下单 / 撤单接口")
public class OrderController {

    private final OrderDisruptorService disruptorService;
    private final SnowflakeIdGenerator idGenerator;

    @PostMapping("/place")
    @Operation(summary = "下单", description = "提交限价单/市价单到撮合引擎")
    public ApiResponse<MatchResultDTO> placeOrder(@Valid @RequestBody PlaceOrderRequest req) {
        Order order = Order.builder()
                .orderId(idGenerator.nextId())
                .userId(req.getUserId())
                .symbol(req.getSymbol())
                .side(OrderSide.valueOf(req.getSide()))
                .type(OrderType.valueOf(req.getType()))
                .timeInForce(req.getTimeInForce() != null
                        ? TimeInForce.valueOf(req.getTimeInForce()) : TimeInForce.GTC)
                .price(req.getPrice())
                .quantity(req.getQuantity())
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.NEW)
                .triggerPrice(req.getStopPrice())
                .displayQuantity(req.getDisplayQuantity())
                .sequenceTime(System.currentTimeMillis())
                .build();

        try {
            MatchResultDTO result = disruptorService.submitOrder(order, 5000);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("下单失败: {}", e.getMessage());
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @PostMapping("/cancel")
    @Operation(summary = "撤单", description = "取消指定订单")
    public ApiResponse<String> cancelOrder(@Valid @RequestBody CancelOrderRequest req) {
        try {
            disruptorService.cancelOrder(req.getSymbol(), req.getOrderId(), 5000);
            return ApiResponse.success("撤单成功");
        } catch (Exception e) {
            log.error("撤单失败: {}", e.getMessage());
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
