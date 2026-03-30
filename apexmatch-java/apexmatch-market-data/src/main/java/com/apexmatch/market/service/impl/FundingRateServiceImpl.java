package com.apexmatch.market.service.impl;

import com.apexmatch.market.service.FundingRateService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资金费率服务实现。
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
public class FundingRateServiceImpl implements FundingRateService {

    private static final BigDecimal MAX_FUNDING_RATE = new BigDecimal("0.0075"); // 0.75%
    private static final BigDecimal MIN_FUNDING_RATE = new BigDecimal("-0.0075"); // -0.75%
    private static final long FUNDING_INTERVAL_MS = 8 * 60 * 60 * 1000L; // 8 小时
    private static final int SCALE = 8;

    private final ConcurrentHashMap<String, BigDecimal> fundingRates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> nextFundingTimes = new ConcurrentHashMap<>();

    @Override
    public BigDecimal getCurrentFundingRate(String symbol) {
        return fundingRates.getOrDefault(symbol, BigDecimal.ZERO);
    }

    @Override
    public void calculateFundingRate(String symbol, BigDecimal markPrice, BigDecimal indexPrice) {
        if (indexPrice.signum() == 0) {
            log.warn("指数价格为 0，无法计算资金费率 symbol={}", symbol);
            return;
        }

        // fundingRate = (markPrice - indexPrice) / indexPrice
        BigDecimal rate = markPrice.subtract(indexPrice)
                .divide(indexPrice, SCALE, RoundingMode.HALF_UP);

        // 限制在 [-0.75%, 0.75%] 范围内
        if (rate.compareTo(MAX_FUNDING_RATE) > 0) {
            rate = MAX_FUNDING_RATE;
        } else if (rate.compareTo(MIN_FUNDING_RATE) < 0) {
            rate = MIN_FUNDING_RATE;
        }

        fundingRates.put(symbol, rate);
        log.info("资金费率更新 symbol={} markPrice={} indexPrice={} fundingRate={}",
                symbol, markPrice, indexPrice, rate);
    }

    @Override
    public long getNextFundingTime(String symbol) {
        return nextFundingTimes.computeIfAbsent(symbol, k -> {
            // 计算下一个整点时间（00:00, 08:00, 16:00 UTC）
            long now = System.currentTimeMillis();
            long nextTime = ((now / FUNDING_INTERVAL_MS) + 1) * FUNDING_INTERVAL_MS;
            return nextTime;
        });
    }
}
