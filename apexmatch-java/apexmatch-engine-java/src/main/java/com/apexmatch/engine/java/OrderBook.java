package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.engine.api.dto.DepthLevelDTO;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 基于 {@link ConcurrentSkipListMap} 的订单簿实现。
 * <p>
 * 买盘（bids）以价格降序排列（最高买价优先），
 * 卖盘（asks）以价格升序排列（最低卖价优先）。
 * 同价位内按 FIFO 时间优先。
 * </p>
 * <p>
 * 撤单采用<b>惰性删除</b>策略：将订单标记为 CANCELED 并从索引移除，
 * 撮合迭代时跳过并清理已取消订单，从而保证撤单操作 O(1)。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
public class OrderBook {

    private final String symbol;

    /** 买盘：价格降序（最高优先） */
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());

    /** 卖盘：价格升序（最低优先） */
    private final ConcurrentSkipListMap<BigDecimal, LinkedList<Order>> asks =
            new ConcurrentSkipListMap<>();

    /** O(1) 通过 orderId 定位订单 */
    private final ConcurrentHashMap<Long, Order> orderIndex = new ConcurrentHashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * 将订单挂入对应买/卖盘。
     */
    public void addOrder(Order order) {
        NavigableMap<BigDecimal, LinkedList<Order>> book = bookFor(order.getSide());
        book.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    /**
     * O(1) 惰性撤单：标记并从索引移除，不立即从价格队列删除。
     *
     * @return 被撤订单；不存在则返回 null
     */
    public Order cancelOrder(long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order != null) {
            order.setStatus(OrderStatus.CANCELED);
        }
        return order;
    }

    /**
     * 仅从索引移除（maker 全部成交后调用）。
     */
    void removeFromIndex(long orderId) {
        orderIndex.remove(orderId);
    }

    public Order getOrder(long orderId) {
        return orderIndex.get(orderId);
    }

    public boolean containsOrder(long orderId) {
        return orderIndex.containsKey(orderId);
    }

    /**
     * 返回 taker 需要撮合的对手盘。
     */
    NavigableMap<BigDecimal, LinkedList<Order>> oppositeSide(OrderSide takerSide) {
        return takerSide == OrderSide.BUY ? asks : bids;
    }

    NavigableMap<BigDecimal, LinkedList<Order>> bookFor(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    // ==================== 盘口深度查询 ====================

    /**
     * 获取买盘深度（按价格降序）。
     */
    public List<DepthLevelDTO> getBidDepth(int levels) {
        return buildDepth(bids, levels);
    }

    /**
     * 获取卖盘深度（按价格升序）。
     */
    public List<DepthLevelDTO> getAskDepth(int levels) {
        return buildDepth(asks, levels);
    }

    private List<DepthLevelDTO> buildDepth(NavigableMap<BigDecimal, LinkedList<Order>> book, int levels) {
        List<DepthLevelDTO> result = new ArrayList<>(levels);
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : book.entrySet()) {
            if (result.size() >= levels) break;
            BigDecimal totalQty = BigDecimal.ZERO;
            for (Order o : entry.getValue()) {
                if (o.getStatus() == OrderStatus.CANCELED) continue;
                BigDecimal visibleQty = remaining(o);
                if (o.getType() == OrderType.ICEBERG && o.getDisplayQuantity() != null) {
                    visibleQty = visibleQty.min(o.getDisplayQuantity());
                }
                totalQty = totalQty.add(visibleQty);
            }
            if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                result.add(DepthLevelDTO.builder().price(entry.getKey()).quantity(totalQty).build());
            }
        }
        return result;
    }

    // ==================== 快照 / 恢复支持 ====================

    /**
     * 返回当前簿内所有有效订单（供快照持久化）。
     */
    public List<Order> allActiveOrders() {
        return new ArrayList<>(orderIndex.values());
    }

    /**
     * 订单簿内有效订单数量。
     */
    public int size() {
        return orderIndex.size();
    }

    // ==================== 工具方法 ====================

    static BigDecimal remaining(Order order) {
        return order.getQuantity().subtract(order.getFilledQuantity());
    }

    /**
     * 冰山单当前可见切片剩余可撮合量。普通订单等价于 {@link #remaining(Order)}。
     * <p>
     * 利用 filledQuantity 对 displayQuantity 取模，自动跟踪当前切片。
     * </p>
     */
    static BigDecimal clipRemaining(Order order) {
        BigDecimal totalRemaining = remaining(order);
        if (order.getType() == OrderType.ICEBERG
                && order.getDisplayQuantity() != null
                && order.getDisplayQuantity().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal clipFilled = order.getFilledQuantity().remainder(order.getDisplayQuantity());
            BigDecimal clipLeft = order.getDisplayQuantity().subtract(clipFilled);
            return clipLeft.min(totalRemaining);
        }
        return totalRemaining;
    }
}
