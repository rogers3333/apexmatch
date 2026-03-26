package com.apexmatch.engine.java;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.common.enums.OrderStatus;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.enums.TimeInForce;
import com.apexmatch.common.util.SnowflakeIdGenerator;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.EngineInitOptions;
import com.apexmatch.engine.api.dto.MarketDepthDTO;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.apexmatch.engine.java.OrderBook.clipRemaining;
import static com.apexmatch.engine.java.OrderBook.remaining;

/**
 * Java 原生撮合引擎：单线程撮合逻辑，支持限价 / 市价 / 止损 / FOK / IOC / 冰山单。
 * <p>
 * 每个交易对独立一个 {@link OrderBook} + {@link StopOrderBook}，
 * 后续可接入 Disruptor 做异步队列缓冲。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
public class JavaMatchingEngine implements MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(JavaMatchingEngine.class);

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, StopOrderBook> stopOrderBooks = new ConcurrentHashMap<>();
    private final SnowflakeIdGenerator tradeIdGen;

    private WalManager walManager;

    public JavaMatchingEngine() {
        this(new SnowflakeIdGenerator(0, 0));
    }

    public JavaMatchingEngine(SnowflakeIdGenerator tradeIdGen) {
        this.tradeIdGen = tradeIdGen;
    }

    // ==================== MatchingEngine 接口实现 ====================

    @Override
    public void init(String symbol, EngineInitOptions options) {
        orderBooks.computeIfAbsent(symbol, OrderBook::new);
        stopOrderBooks.computeIfAbsent(symbol, k -> new StopOrderBook());

        if (options != null && options.getWalDirectory() != null) {
            this.walManager = new WalManager(options.getWalDirectory(), symbol);
            if (options.isRecoverFromSnapshot()) {
                recoverFromWal(symbol);
            }
        }
        log.info("引擎初始化完成: symbol={}", symbol);
    }

    @Override
    public MatchResultDTO submitOrder(Order order) {
        ensureInitialized(order.getSymbol());

        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(BigDecimal.ZERO);
        }

        if (walManager != null) {
            walManager.appendSubmit(order);
        }

        if (order.getType() == OrderType.STOP_LIMIT || order.getType() == OrderType.STOP_MARKET) {
            return handleStopOrder(order);
        }

        return matchAndPlace(order);
    }

    @Override
    public Optional<Boolean> cancelOrder(String symbol, long orderId) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) return Optional.empty();

        Order canceled = book.cancelOrder(orderId);
        if (canceled != null) {
            if (walManager != null) {
                walManager.appendCancel(symbol, orderId);
            }
            return Optional.of(true);
        }

        StopOrderBook stopBook = stopOrderBooks.get(symbol);
        if (stopBook != null) {
            Order stopCanceled = stopBook.removeStopOrder(orderId);
            if (stopCanceled != null) {
                if (walManager != null) {
                    walManager.appendCancel(symbol, orderId);
                }
                return Optional.of(true);
            }
        }

        return Optional.empty();
    }

    @Override
    public MarketDepthDTO getMarketDepth(String symbol, int levels) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            return MarketDepthDTO.builder()
                    .symbol(symbol)
                    .generatedTime(System.currentTimeMillis())
                    .build();
        }
        return MarketDepthDTO.builder()
                .symbol(symbol)
                .bids(book.getBidDepth(levels))
                .asks(book.getAskDepth(levels))
                .generatedTime(System.currentTimeMillis())
                .build();
    }

    // ==================== 核心撮合 ====================

    /**
     * 对 taker 执行撮合，未成交部分按策略挂单或取消。
     */
    private MatchResultDTO matchAndPlace(Order taker) {
        OrderBook book = orderBooks.get(taker.getSymbol());
        TimeInForce tif = taker.getTimeInForce() != null ? taker.getTimeInForce() : TimeInForce.GTC;

        // FOK 预检：能否完全成交
        if (tif == TimeInForce.FOK) {
            if (!canFillCompletely(book, taker)) {
                taker.setStatus(OrderStatus.REJECTED);
                return MatchResultDTO.builder()
                        .affectedOrder(taker)
                        .rejectReason("FOK_CANNOT_FILL")
                        .build();
            }
        }

        MatchResultDTO result = doMatch(book, taker);

        BigDecimal leftover = remaining(taker);
        boolean hasFill = !result.getTrades().isEmpty();

        if (leftover.compareTo(BigDecimal.ZERO) > 0) {
            switch (tif) {
                case IOC -> {
                    taker.setStatus(hasFill ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED);
                }
                case FOK -> {
                    // 预检通过后理论不会走到这里
                    taker.setStatus(OrderStatus.FILLED);
                }
                case GTC -> {
                    if (taker.getType() == OrderType.MARKET) {
                        taker.setStatus(hasFill ? OrderStatus.PARTIALLY_FILLED : OrderStatus.CANCELED);
                    } else {
                        book.addOrder(taker);
                        taker.setStatus(hasFill ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW);
                    }
                }
            }
        }

        result.setAffectedOrder(taker);

        // 有成交时检查止损单触发
        if (!result.getTrades().isEmpty()) {
            BigDecimal lastPrice = result.getTrades().get(result.getTrades().size() - 1).getPrice();
            List<MatchResultDTO> stopResults = triggerStopOrders(taker.getSymbol(), lastPrice);
            for (MatchResultDTO sr : stopResults) {
                result.getTrades().addAll(sr.getTrades());
            }
        }

        return result;
    }

    /**
     * 价格优先 / 时间优先撮合核心。支持冰山单切片逻辑。
     * <p>
     * 内层使用 {@code peekFirst/pollFirst} 驱动，而非 Iterator，
     * 确保冰山切片耗尽后重排至队尾的订单在同一价位内可被再次撮合。
     * </p>
     */
    private MatchResultDTO doMatch(OrderBook book, Order taker) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal takerRemaining = remaining(taker);

        NavigableMap<BigDecimal, LinkedList<Order>> opposite = book.oppositeSide(taker.getSide());
        Iterator<Map.Entry<BigDecimal, LinkedList<Order>>> priceIt = opposite.entrySet().iterator();

        while (priceIt.hasNext() && takerRemaining.compareTo(BigDecimal.ZERO) > 0) {
            Map.Entry<BigDecimal, LinkedList<Order>> entry = priceIt.next();
            BigDecimal makerPrice = entry.getKey();

            if (!priceAcceptable(taker, makerPrice)) break;

            LinkedList<Order> queue = entry.getValue();

            while (takerRemaining.compareTo(BigDecimal.ZERO) > 0) {
                Order maker = queue.peekFirst();
                if (maker == null) break;

                if (maker.getStatus() == OrderStatus.CANCELED) {
                    queue.pollFirst();
                    continue;
                }

                BigDecimal makerMatchable = clipRemaining(maker);
                if (makerMatchable.compareTo(BigDecimal.ZERO) <= 0) {
                    queue.pollFirst();
                    continue;
                }

                BigDecimal matchQty = takerRemaining.min(makerMatchable);
                Trade trade = buildTrade(taker, maker, makerPrice, matchQty);
                trades.add(trade);

                maker.setFilledQuantity(maker.getFilledQuantity().add(matchQty));
                takerRemaining = takerRemaining.subtract(matchQty);

                BigDecimal makerTotalRemaining = remaining(maker);
                if (makerTotalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    maker.setStatus(OrderStatus.FILLED);
                    queue.pollFirst();
                    book.removeFromIndex(maker.getOrderId());
                } else if (maker.getType() == OrderType.ICEBERG
                        && clipRemaining(maker).compareTo(BigDecimal.ZERO) <= 0) {
                    maker.setStatus(OrderStatus.PARTIALLY_FILLED);
                    queue.pollFirst();
                    queue.addLast(maker);
                } else {
                    maker.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
            }

            if (queue.isEmpty()) {
                priceIt.remove();
            }
        }

        taker.setFilledQuantity(taker.getQuantity().subtract(takerRemaining));
        if (takerRemaining.compareTo(BigDecimal.ZERO) == 0) {
            taker.setStatus(OrderStatus.FILLED);
        } else if (taker.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            taker.setStatus(OrderStatus.PARTIALLY_FILLED);
        }

        return MatchResultDTO.builder().trades(trades).affectedOrder(taker).build();
    }

    // ==================== 止损单处理 ====================

    private MatchResultDTO handleStopOrder(Order order) {
        StopOrderBook stopBook = stopOrderBooks.get(order.getSymbol());
        stopBook.addStopOrder(order);
        order.setStatus(OrderStatus.NEW);
        return MatchResultDTO.builder().affectedOrder(order).build();
    }

    /**
     * 最新成交价驱动止损触发，被触发的订单转为限价/市价单进入撮合。
     */
    private List<MatchResultDTO> triggerStopOrders(String symbol, BigDecimal lastPrice) {
        StopOrderBook stopBook = stopOrderBooks.get(symbol);
        if (stopBook == null) return Collections.emptyList();

        List<Order> triggered = stopBook.checkTriggers(lastPrice);
        List<MatchResultDTO> results = new ArrayList<>();

        for (Order order : triggered) {
            if (order.getType() == OrderType.STOP_MARKET) {
                order.setType(OrderType.MARKET);
            } else if (order.getType() == OrderType.STOP_LIMIT) {
                order.setType(OrderType.LIMIT);
            }
            results.add(matchAndPlace(order));
        }

        return results;
    }

    // ==================== 辅助方法 ====================

    /**
     * FOK 预检：对手盘在可接受价格范围内的总量是否 ≥ taker 剩余。
     */
    private boolean canFillCompletely(OrderBook book, Order taker) {
        BigDecimal needed = remaining(taker);
        NavigableMap<BigDecimal, LinkedList<Order>> opposite = book.oppositeSide(taker.getSide());

        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : opposite.entrySet()) {
            if (!priceAcceptable(taker, entry.getKey())) break;
            for (Order maker : entry.getValue()) {
                if (maker.getStatus() == OrderStatus.CANCELED) continue;
                needed = needed.subtract(clipRemaining(maker));
                if (needed.compareTo(BigDecimal.ZERO) <= 0) return true;
            }
        }
        return false;
    }

    /**
     * 检查 maker 价格是否满足 taker 约束（限价单才有约束）。
     */
    private boolean priceAcceptable(Order taker, BigDecimal makerPrice) {
        if (taker.getType() == OrderType.MARKET) return true;
        if (taker.getSide() == OrderSide.BUY) {
            return makerPrice.compareTo(taker.getPrice()) <= 0;
        } else {
            return makerPrice.compareTo(taker.getPrice()) >= 0;
        }
    }

    private Trade buildTrade(Order taker, Order maker, BigDecimal price, BigDecimal qty) {
        return Trade.builder()
                .tradeId(tradeIdGen.nextId())
                .symbol(taker.getSymbol())
                .price(price)
                .quantity(qty)
                .takerOrderId(taker.getOrderId())
                .makerOrderId(maker.getOrderId())
                .takerUserId(taker.getUserId())
                .makerUserId(maker.getUserId())
                .tradeTime(System.currentTimeMillis())
                .build();
    }

    private void ensureInitialized(String symbol) {
        orderBooks.computeIfAbsent(symbol, OrderBook::new);
        stopOrderBooks.computeIfAbsent(symbol, k -> new StopOrderBook());
    }

    // ==================== WAL 恢复 ====================

    private void recoverFromWal(String symbol) {
        if (walManager == null) return;
        List<WalEntry> entries = walManager.readAll();
        for (WalEntry e : entries) {
            switch (e.getType()) {
                case SUBMIT -> {
                    Order order = e.getOrder();
                    if (order.getType() == OrderType.STOP_LIMIT || order.getType() == OrderType.STOP_MARKET) {
                        handleStopOrder(order);
                    } else {
                        matchAndPlace(order);
                    }
                }
                case CANCEL -> cancelOrder(e.getSymbol(), e.getOrderId());
            }
        }
        log.info("WAL 恢复完成: symbol={}, entries={}", symbol, entries.size());
    }

    // ==================== 暴露给测试 ====================

    OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    StopOrderBook getStopOrderBook(String symbol) {
        return stopOrderBooks.get(symbol);
    }
}
