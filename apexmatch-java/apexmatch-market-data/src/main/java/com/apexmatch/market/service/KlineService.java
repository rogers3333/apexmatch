package com.apexmatch.market.service;

import com.apexmatch.common.entity.Kline;
import com.apexmatch.common.entity.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * K 线聚合服务。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface KlineService {

    /** 根据成交更新对应周期的 K 线 */
    void onTrade(Trade trade);

    /** 获取指定交易对和周期的最新 K 线 */
    Kline getLatest(String symbol, String interval);

    /** 获取指定交易对和周期的 K 线列表 */
    List<Kline> getKlines(String symbol, String interval, int limit);

    /**
     * 获取 24h 行情统计。
     * 包括：24h 成交量、涨跌幅、最高价、最低价、开盘价、收盘价。
     */
    Ticker24h getTicker24h(String symbol);

    /**
     * 24h 行情统计数据
     */
    record Ticker24h(
            String symbol,
            BigDecimal lastPrice,      // 最新价
            BigDecimal priceChange,    // 24h 价格变化
            BigDecimal priceChangePercent, // 24h 涨跌幅 (%)
            BigDecimal highPrice,      // 24h 最高价
            BigDecimal lowPrice,       // 24h 最低价
            BigDecimal volume,         // 24h 成交量
            BigDecimal quoteVolume,    // 24h 成交额
            long openTime,             // 24h 开始时间
            long closeTime             // 24h 结束时间
    ) {}
}
