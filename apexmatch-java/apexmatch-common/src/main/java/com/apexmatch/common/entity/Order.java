package com.apexmatch.common.entity;

import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 撮合与业务层共用的订单实体。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * 系统订单号（全局唯一）
     */
    private Long orderId;

    /**
     * 客户端订单号（幂等键，同一用户下唯一）
     */
    private String clientOrderId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 交易对符号，如 BTC-USDT
     */
    private String symbol;

    /**
     * 买卖方向
     */
    private OrderSide side;

    /**
     * 订单类型
     */
    private OrderType type;

    /**
     * 有效期策略
     */
    private TimeInForce timeInForce;

    /**
     * 限价；市价单可为 null
     */
    private BigDecimal price;

    /**
     * 原始委托数量
     */
    private BigDecimal quantity;

    /**
     * 已成交数量
     */
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    /**
     * 止损/止损限价触发价；非止损单可为 null
     */
    private BigDecimal triggerPrice;

    /**
     * 止盈价（触发后转市价，由业务层处理）
     */
    private BigDecimal takeProfitPrice;

    /**
     * 止损价（触发后转市价）
     */
    private BigDecimal stopLossPrice;

    /**
     * 订单状态
     */
    private OrderStatus status;

    /**
     * 进入订单簿或系统受理时间（毫秒），用于时间优先
     */
    private long sequenceTime;

    /**
     * 显示数量（冰山单）；null 表示全额展示
     */
    private BigDecimal displayQuantity;
}
