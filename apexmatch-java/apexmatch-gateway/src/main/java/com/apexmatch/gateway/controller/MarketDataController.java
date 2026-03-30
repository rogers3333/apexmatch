package com.apexmatch.gateway.controller;

import com.apexmatch.common.entity.Kline;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.MarketDepthDTO;
import com.apexmatch.gateway.dto.ApiResponse;
import com.apexmatch.market.service.KlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行情数据 REST API。
 *
 * @author luka
 * @since 2025-03-26
 */
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
@Tag(name = "行情数据", description = "盘口深度 / K线查询")
public class MarketDataController {

    private final MatchingEngine matchingEngine;
    private final KlineService klineService;

    @GetMapping("/depth/{symbol}")
    @Operation(summary = "盘口深度", description = "获取指定交易对的买卖盘深度")
    public ApiResponse<MarketDepthDTO> getDepth(@PathVariable String symbol,
                                                 @RequestParam(defaultValue = "20") int levels) {
        return ApiResponse.success(matchingEngine.getMarketDepth(symbol, levels));
    }

    @GetMapping("/klines/{symbol}")
    @Operation(summary = "K线数据", description = "获取指定交易对的K线")
    public ApiResponse<List<Kline>> getKlines(@PathVariable String symbol,
                                               @RequestParam(defaultValue = "1m") String interval,
                                               @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(klineService.getKlines(symbol, interval, limit));
    }

    @GetMapping("/ticker/{symbol}")
    @Operation(summary = "24h 行情统计", description = "获取指定交易对的 24h 成交量、涨跌幅等统计数据")
    public ApiResponse<KlineService.Ticker24h> getTicker24h(@PathVariable String symbol) {
        return ApiResponse.success(klineService.getTicker24h(symbol));
    }
}
