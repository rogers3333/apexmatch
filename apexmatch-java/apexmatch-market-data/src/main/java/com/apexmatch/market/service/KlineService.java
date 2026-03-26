package com.apexmatch.market.service;

import com.apexmatch.common.entity.Kline;
import com.apexmatch.common.entity.Trade;

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
}
