package com.apexmatch.market.service.impl;

import com.apexmatch.market.service.MarkPriceService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 标记价格服务实现。
 * 使用 EMA（指数移动平均）平滑处理，避免瞬时波动。
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
public class MarkPriceServiceImpl implements MarkPriceService {

    private static final BigDecimal EMA_ALPHA = new BigDecimal("0.3"); // 平滑系数
    private static final int SCALE = 8;

    private final ConcurrentHashMap<String, BigDecimal> markPrices = new ConcurrentHashMap<>();

    @Override
    public BigDecimal getMarkPrice(String symbol) {
        return markPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }

    @Override
    public void updateMarkPrice(String symbol, BigDecimal lastTradePrice, BigDecimal bidPrice, BigDecimal askPrice) {
        // 计算盘口中间价
        BigDecimal midPrice = BigDecimal.ZERO;
        if (bidPrice != null && askPrice != null && bidPrice.signum() > 0 && askPrice.signum() > 0) {
            midPrice = bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
        }

        // 计算原始标记价格：(最新成交价 + 盘口中间价) / 2
        BigDecimal rawMarkPrice;
        if (midPrice.signum() > 0) {
            rawMarkPrice = lastTradePrice.add(midPrice).divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
        } else {
            rawMarkPrice = lastTradePrice;
        }

        // EMA 平滑处理
        BigDecimal currentMarkPrice = markPrices.get(symbol);
        BigDecimal newMarkPrice;
        if (currentMarkPrice == null || currentMarkPrice.signum() == 0) {
            newMarkPrice = rawMarkPrice;
        } else {
            // EMA = alpha * newPrice + (1 - alpha) * oldPrice
            newMarkPrice = EMA_ALPHA.multiply(rawMarkPrice)
                    .add(BigDecimal.ONE.subtract(EMA_ALPHA).multiply(currentMarkPrice))
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        markPrices.put(symbol, newMarkPrice);
        log.debug("标记价格更新 symbol={} lastTrade={} mid={} mark={}",
                symbol, lastTradePrice, midPrice, newMarkPrice);
    }
}
