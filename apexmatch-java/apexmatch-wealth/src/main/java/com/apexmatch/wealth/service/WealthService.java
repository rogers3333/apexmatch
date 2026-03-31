package com.apexmatch.wealth.service;

import com.apexmatch.wealth.entity.*;
import com.apexmatch.fundchain.service.FundChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WealthService {

    private final FundChainService fundChainService;
    private final Map<Long, WealthProduct> products = new ConcurrentHashMap<>();
    private final Map<Long, Investment> investments = new ConcurrentHashMap<>();
    private final List<ProfitDistribution> distributions = new ArrayList<>();

    public WealthProduct createProduct(String productName, String productType, String currencyCode,
                                       BigDecimal annualRate, Integer lockDays, BigDecimal minInvestAmount,
                                       BigDecimal maxInvestAmount, BigDecimal totalQuota) {
        WealthProduct product = new WealthProduct();
        product.setProductId(System.currentTimeMillis());
        product.setProductName(productName);
        product.setProductType(productType);
        product.setCurrencyCode(currencyCode);
        product.setAnnualRate(annualRate);
        product.setLockDays(lockDays);
        product.setMinInvestAmount(minInvestAmount);
        product.setMaxInvestAmount(maxInvestAmount);
        product.setTotalQuota(totalQuota);
        product.setRemainingQuota(totalQuota);
        product.setIsActive(true);
        product.setStartAt(LocalDateTime.now());
        product.setCreatedAt(LocalDateTime.now());
        products.put(product.getProductId(), product);
        log.info("创建理财产品: productId={}, name={}, rate={}", product.getProductId(), productName, annualRate);
        return product;
    }

    public Investment invest(Long userId, Long productId, BigDecimal amount) {
        WealthProduct product = products.get(productId);
        if (product == null || !product.getIsActive()) {
            throw new RuntimeException("产品不存在或已下架");
        }
        if (amount.compareTo(product.getMinInvestAmount()) < 0 || amount.compareTo(product.getMaxInvestAmount()) > 0) {
            throw new RuntimeException("投资金额不在范围内");
        }
        if (amount.compareTo(product.getRemainingQuota()) > 0) {
            throw new RuntimeException("超过剩余额度");
        }

        Investment investment = new Investment();
        investment.setInvestmentId(System.currentTimeMillis());
        investment.setUserId(userId);
        investment.setProductId(productId);
        investment.setAmount(amount);
        BigDecimal expectedReturn = amount.multiply(product.getAnnualRate())
                .multiply(new BigDecimal(product.getLockDays()))
                .divide(new BigDecimal("365"), 8, RoundingMode.HALF_UP);
        investment.setExpectedReturn(expectedReturn);
        investment.setActualReturn(BigDecimal.ZERO);
        investment.setStatus("ACTIVE");
        investment.setInvestedAt(LocalDateTime.now());
        investment.setMaturityAt(LocalDateTime.now().plusDays(product.getLockDays()));
        investment.setCreatedAt(LocalDateTime.now());
        investments.put(investment.getInvestmentId(), investment);

        product.setRemainingQuota(product.getRemainingQuota().subtract(amount));
        product.setUpdatedAt(LocalDateTime.now());

        fundChainService.recordFundFlow(userId, product.getCurrencyCode(), null,
                "WEALTH_INVEST", amount.negate(), BigDecimal.ZERO, "WEALTH", investment.getInvestmentId().toString(), null);
        log.info("用户投资: investmentId={}, userId={}, amount={}", investment.getInvestmentId(), userId, amount);
        return investment;
    }

    public void distributeProfit(Long investmentId) {
        Investment investment = investments.get(investmentId);
        if (investment == null || !"ACTIVE".equals(investment.getStatus())) {
            return;
        }

        WealthProduct product = products.get(investment.getProductId());
        if (product == null) return;

        ProfitDistribution distribution = new ProfitDistribution();
        distribution.setDistributionId(System.currentTimeMillis());
        distribution.setInvestmentId(investmentId);
        distribution.setUserId(investment.getUserId());
        distribution.setCurrencyCode(product.getCurrencyCode());
        distribution.setProfitAmount(investment.getExpectedReturn());
        distribution.setStatus("DISTRIBUTED");
        distribution.setDistributedAt(LocalDateTime.now());
        distribution.setCreatedAt(LocalDateTime.now());
        distributions.add(distribution);

        investment.setActualReturn(investment.getExpectedReturn());
        investment.setUpdatedAt(LocalDateTime.now());

        fundChainService.recordFundFlow(investment.getUserId(), product.getCurrencyCode(), null,
                "WEALTH_PROFIT", distribution.getProfitAmount(), BigDecimal.ZERO,
                "WEALTH", investmentId.toString(), null);
        log.info("分润: distributionId={}, userId={}, profit={}", distribution.getDistributionId(),
                 investment.getUserId(), distribution.getProfitAmount());
    }

    public void redeem(Long investmentId) {
        Investment investment = investments.get(investmentId);
        if (investment == null || !"ACTIVE".equals(investment.getStatus())) {
            throw new RuntimeException("投资不存在或已赎回");
        }

        WealthProduct product = products.get(investment.getProductId());
        investment.setStatus("REDEEMED");
        investment.setRedeemedAt(LocalDateTime.now());
        investment.setUpdatedAt(LocalDateTime.now());

        fundChainService.recordFundFlow(investment.getUserId(), product.getCurrencyCode(), null,
                "WEALTH_REDEEM", investment.getAmount(), BigDecimal.ZERO, "WEALTH", investmentId.toString(), null);
        log.info("赎回: investmentId={}, userId={}, amount={}", investmentId, investment.getUserId(), investment.getAmount());
    }

    public List<WealthProduct> getActiveProducts() {
        return products.values().stream()
                .filter(WealthProduct::getIsActive)
                .toList();
    }

    public List<Investment> getUserInvestments(Long userId) {
        return investments.values().stream()
                .filter(i -> i.getUserId().equals(userId))
                .toList();
    }
}
