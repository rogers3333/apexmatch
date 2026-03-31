package com.apexmatch.wealth.controller;

import com.apexmatch.wealth.entity.*;
import com.apexmatch.wealth.service.WealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wealth")
@RequiredArgsConstructor
public class WealthController {

    private final WealthService wealthService;

    @PostMapping("/product/create")
    public WealthProduct createProduct(@RequestBody Map<String, Object> req) {
        return wealthService.createProduct(
                req.get("productName").toString(),
                req.get("productType").toString(),
                req.get("currencyCode").toString(),
                new java.math.BigDecimal(req.get("annualRate").toString()),
                Integer.valueOf(req.get("lockDays").toString()),
                new java.math.BigDecimal(req.get("minInvestAmount").toString()),
                new java.math.BigDecimal(req.get("maxInvestAmount").toString()),
                new java.math.BigDecimal(req.get("totalQuota").toString())
        );
    }

    @GetMapping("/product/list")
    public List<WealthProduct> listProducts() {
        return wealthService.getActiveProducts();
    }

    @PostMapping("/invest")
    public Investment invest(@RequestBody Map<String, Object> req) {
        return wealthService.invest(
                Long.valueOf(req.get("userId").toString()),
                Long.valueOf(req.get("productId").toString()),
                new java.math.BigDecimal(req.get("amount").toString())
        );
    }

    @PostMapping("/redeem/{investmentId}")
    public void redeem(@PathVariable Long investmentId) {
        wealthService.redeem(investmentId);
    }

    @PostMapping("/distribute/{investmentId}")
    public void distributeProfit(@PathVariable Long investmentId) {
        wealthService.distributeProfit(investmentId);
    }

    @GetMapping("/investment/{userId}")
    public List<Investment> getUserInvestments(@PathVariable Long userId) {
        return wealthService.getUserInvestments(userId);
    }
}
