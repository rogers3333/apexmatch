package com.apexmatch.dex.controller;

import com.apexmatch.dex.entity.*;
import com.apexmatch.dex.service.DexAggregatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dex")
@RequiredArgsConstructor
public class DexController {

    private final DexAggregatorService dexService;

    @PostMapping("/protocol/add")
    public DexProtocol addProtocol(@RequestBody Map<String, Object> req) {
        return dexService.addProtocol(
                req.get("protocolName").toString(),
                req.get("chainCode").toString(),
                req.get("routerAddress").toString(),
                new java.math.BigDecimal(req.get("feeRate").toString())
        );
    }

    @PostMapping("/pool/add")
    public LiquidityPool addPool(@RequestBody Map<String, Object> req) {
        return dexService.addPool(
                Long.valueOf(req.get("protocolId").toString()),
                req.get("chainCode").toString(),
                req.get("poolAddress").toString(),
                req.get("token0").toString(),
                req.get("token1").toString(),
                new java.math.BigDecimal(req.get("reserve0").toString()),
                new java.math.BigDecimal(req.get("reserve1").toString())
        );
    }

    @PostMapping("/quote")
    public SwapRoute getQuote(@RequestBody Map<String, Object> req) {
        return dexService.findBestRoute(
                req.get("fromToken").toString(),
                req.get("toToken").toString(),
                new java.math.BigDecimal(req.get("amountIn").toString())
        );
    }

    @PostMapping("/swap")
    public DexSwapRecord executeSwap(@RequestBody Map<String, Object> req) {
        SwapRoute route = dexService.findBestRoute(
                req.get("fromToken").toString(),
                req.get("toToken").toString(),
                new java.math.BigDecimal(req.get("amountIn").toString())
        );
        return dexService.executeSwap(
                Long.valueOf(req.get("userId").toString()),
                req.get("chainCode").toString(),
                route
        );
    }

    @GetMapping("/protocols")
    public List<DexProtocol> getProtocols() {
        return dexService.getActiveProtocols();
    }

    @GetMapping("/swaps/{userId}")
    public List<DexSwapRecord> getUserSwaps(@PathVariable Long userId) {
        return dexService.getUserSwaps(userId);
    }
}
