package com.apexmatch.market.service;

import java.math.BigDecimal;

/**
 * 标记价格服务。
 * 标记价格用于强平触发，避免因瞬时价格波动导致不合理强平。
 * 计算方式：(最新成交价 + 盘口中间价) / 2，并做平滑处理。
 *
 * @author luka
 * @since 2025-03-30
 */
public interface MarkPriceService {

    /**
     * 获取当前标记价格
     */
    BigDecimal getMarkPrice(String symbol);

    /**
     * 更新标记价格（每笔成交后调用）
     */
    void updateMarkPrice(String symbol, BigDecimal lastTradePrice, BigDecimal bidPrice, BigDecimal askPrice);
}
