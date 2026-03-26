package com.apexmatch.market.service.impl;

import com.apexmatch.common.entity.Kline;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.market.service.KlineService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K 线聚合内存实现。
 * 支持 1m / 5m / 15m / 1h / 4h / 1d 六种周期。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class KlineServiceImpl implements KlineService {

    private static final String[] INTERVALS = {"1m", "5m", "15m", "1h", "4h", "1d"};

    /** key = symbol:interval, value = 按 openTime 排序的 K 线 */
    private final ConcurrentHashMap<String, LinkedList<Kline>> store = new ConcurrentHashMap<>();

    @Override
    public void onTrade(Trade trade) {
        for (String interval : INTERVALS) {
            long periodMs = periodMs(interval);
            long openTime = trade.getTradeTime() / periodMs * periodMs;
            long closeTime = openTime + periodMs - 1;

            String key = trade.getSymbol() + ":" + interval;
            LinkedList<Kline> klines = store.computeIfAbsent(key, k -> new LinkedList<>());

            synchronized (klines) {
                Kline last = klines.isEmpty() ? null : klines.getLast();
                if (last != null && last.getOpenTime() == openTime) {
                    mergeInto(last, trade);
                } else {
                    Kline kline = Kline.builder()
                            .symbol(trade.getSymbol())
                            .interval(interval)
                            .openTime(openTime)
                            .closeTime(closeTime)
                            .open(trade.getPrice())
                            .high(trade.getPrice())
                            .low(trade.getPrice())
                            .close(trade.getPrice())
                            .volume(trade.getQuantity())
                            .turnover(trade.getPrice().multiply(trade.getQuantity()))
                            .tradeCount(1)
                            .build();
                    klines.addLast(kline);
                    if (klines.size() > 1000) {
                        klines.removeFirst();
                    }
                }
            }
        }
    }

    @Override
    public Kline getLatest(String symbol, String interval) {
        LinkedList<Kline> klines = store.get(symbol + ":" + interval);
        if (klines == null || klines.isEmpty()) return null;
        synchronized (klines) {
            return klines.getLast();
        }
    }

    @Override
    public List<Kline> getKlines(String symbol, String interval, int limit) {
        LinkedList<Kline> klines = store.get(symbol + ":" + interval);
        if (klines == null) return List.of();
        synchronized (klines) {
            int size = klines.size();
            int from = Math.max(0, size - limit);
            return new ArrayList<>(klines.subList(from, size));
        }
    }

    private void mergeInto(Kline kline, Trade trade) {
        BigDecimal price = trade.getPrice();
        if (price.compareTo(kline.getHigh()) > 0) kline.setHigh(price);
        if (price.compareTo(kline.getLow()) < 0) kline.setLow(price);
        kline.setClose(price);
        kline.setVolume(kline.getVolume().add(trade.getQuantity()));
        kline.setTurnover(kline.getTurnover().add(price.multiply(trade.getQuantity())));
        kline.setTradeCount(kline.getTradeCount() + 1);
    }

    private long periodMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
    }
}
