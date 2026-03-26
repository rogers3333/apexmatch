package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.*;

/**
 * 止损/止盈订单簿。
 * <p>
 * BUY 止损单：当市场最新价 <b>≥</b> triggerPrice 时触发。<br/>
 * SELL 止损单：当市场最新价 <b>≤</b> triggerPrice 时触发。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
public class StopOrderBook {

    /** BUY 止损：升序排列，价格从小到大扫描即可批量触发 */
    private final TreeMap<BigDecimal, List<Order>> buyStops = new TreeMap<>();

    /** SELL 止损：降序排列，价格从大到小扫描即可批量触发 */
    private final TreeMap<BigDecimal, List<Order>> sellStops = new TreeMap<>(Comparator.reverseOrder());

    /** O(1) 按 orderId 查找 */
    private final Map<Long, Order> index = new HashMap<>();

    public void addStopOrder(Order order) {
        TreeMap<BigDecimal, List<Order>> book =
                order.getSide() == OrderSide.BUY ? buyStops : sellStops;
        book.computeIfAbsent(order.getTriggerPrice(), k -> new ArrayList<>()).add(order);
        index.put(order.getOrderId(), order);
    }

    /**
     * O(1) 撤销止损单。
     *
     * @return 被撤订单；不存在返回 null
     */
    public Order removeStopOrder(long orderId) {
        Order order = index.remove(orderId);
        if (order != null) {
            order.setStatus(OrderStatus.CANCELED);
        }
        return order;
    }

    public boolean contains(long orderId) {
        return index.containsKey(orderId);
    }

    /**
     * 给定最新成交价，返回所有应触发的止损订单（按 triggerPrice 顺序）。
     * 触发后从簿中移除。
     *
     * @param lastPrice 最新成交价
     * @return 已触发的订单列表
     */
    public List<Order> checkTriggers(BigDecimal lastPrice) {
        List<Order> triggered = new ArrayList<>();
        collectTriggered(buyStops, lastPrice, true, triggered);
        collectTriggered(sellStops, lastPrice, false, triggered);
        return triggered;
    }

    private void collectTriggered(TreeMap<BigDecimal, List<Order>> book,
                                  BigDecimal lastPrice, boolean isBuy,
                                  List<Order> out) {
        Iterator<Map.Entry<BigDecimal, List<Order>>> it = book.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BigDecimal, List<Order>> entry = it.next();
            boolean shouldTrigger = isBuy
                    ? lastPrice.compareTo(entry.getKey()) >= 0
                    : lastPrice.compareTo(entry.getKey()) <= 0;
            if (!shouldTrigger) break;

            for (Order o : entry.getValue()) {
                if (o.getStatus() == OrderStatus.CANCELED) continue;
                index.remove(o.getOrderId());
                out.add(o);
            }
            it.remove();
        }
    }

    /**
     * 所有挂起的止损单（供快照持久化）。
     */
    public List<Order> allStopOrders() {
        return new ArrayList<>(index.values());
    }

    public int size() {
        return index.size();
    }
}
