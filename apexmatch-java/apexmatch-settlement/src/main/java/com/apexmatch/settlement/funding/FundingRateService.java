package com.apexmatch.settlement.funding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资金费率服务
 * 负责永续合约资金费率计算、预告、定时结算
 */
@Slf4j
@Service
public class FundingRateService {

    private final Map<String, FundingRate> currentRates = new ConcurrentHashMap<>();
    private final List<FundingSettlement> settlements = new ArrayList<>();

    /**
     * 计算资金费率
     * 公式：资金费率 = 溢价指数 + clamp(利率 - 溢价指数, 0.05%, -0.05%)
     */
    public FundingRate calculateFundingRate(String symbol, BigDecimal premiumIndex, BigDecimal interestRate) {
        BigDecimal diff = interestRate.subtract(premiumIndex);
        BigDecimal clampedDiff = diff.max(new BigDecimal("-0.0005")).min(new BigDecimal("0.0005"));
        BigDecimal fundingRate = premiumIndex.add(clampedDiff);

        FundingRate rate = new FundingRate();
        rate.setSymbol(symbol);
        rate.setFundingRate(fundingRate);
        rate.setPremiumIndex(premiumIndex);
        rate.setInterestRate(interestRate);
        rate.setFundingTime(System.currentTimeMillis());
        rate.setNextFundingTime(LocalDateTime.now().plusHours(8));
        rate.setCreateTime(LocalDateTime.now());

        currentRates.put(symbol, rate);
        log.info("计算资金费率: symbol={}, rate={}, premium={}, interest={}",
                symbol, fundingRate, premiumIndex, interestRate);
        return rate;
    }

    /**
     * 获取当前资金费率
     */
    public FundingRate getCurrentRate(String symbol) {
        return currentRates.get(symbol);
    }
