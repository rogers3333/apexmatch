package com.apexmatch.engine.rust;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.*;
import com.google.gson.*;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;

/**
 * 通过 JNA 调用 Rust 撮合引擎动态库的适配器。
 * 实现 {@link MatchingEngine} 标准接口，使上层业务无感知切换。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class RustMatchingEngine implements MatchingEngine {

    private final RustNativeLibrary lib;
    private final Gson gson;

    public RustMatchingEngine(String libraryPath) {
        this.lib = RustNativeLibrary.load(libraryPath);
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        int abi = lib.apexmatch_engine_abi_version();
        log.info("Rust 引擎加载成功，ABI 版本={}", abi);
    }

    /** 仅用于测试的构造器，允许注入 mock library */
    RustMatchingEngine(RustNativeLibrary lib) {
        this.lib = lib;
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    @Override
    public void init(String symbol, EngineInitOptions options) {
        lib.engine_init(symbol);
        log.info("Rust 引擎初始化交易对 symbol={}", symbol);
    }

    @Override
    public MatchResultDTO submitOrder(Order order) {
        String json = gson.toJson(orderToMap(order));
        Pointer resultPtr = lib.engine_submit_order_json(json);
        if (resultPtr == null) {
            return MatchResultDTO.builder()
                    .trades(List.of())
                    .affectedOrder(order)
                    .rejectReason("FFI_CALL_FAILED")
                    .build();
        }
        try {
            String resultJson = resultPtr.getString(0);
            return parseMatchResult(resultJson, order);
        } finally {
            lib.engine_free_string(resultPtr);
        }
    }

    @Override
    public Optional<Boolean> cancelOrder(String symbol, long orderId) {
        byte result = lib.engine_cancel_order(symbol, orderId);
        return Optional.of(result == 1);
    }

    @Override
    public MarketDepthDTO getMarketDepth(String symbol, int levels) {
        Pointer ptr = lib.engine_get_depth_json(symbol, levels);
        if (ptr == null) {
            return MarketDepthDTO.builder()
                    .symbol(symbol)
                    .bids(List.of())
                    .asks(List.of())
                    .generatedTime(System.currentTimeMillis())
                    .build();
        }
        try {
            String json = ptr.getString(0);
            return parseMarketDepth(json, symbol);
        } finally {
            lib.engine_free_string(ptr);
        }
    }

    private Map<String, Object> orderToMap(Order order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", order.getOrderId());
        m.put("client_order_id", order.getClientOrderId());
        m.put("user_id", order.getUserId());
        m.put("symbol", order.getSymbol());
        m.put("side", order.getSide().name());
        m.put("order_type", order.getType().name());
        m.put("time_in_force", order.getTimeInForce().name());
        m.put("price", order.getPrice() != null ? order.getPrice().toPlainString() : null);
        m.put("quantity", order.getQuantity().toPlainString());
        m.put("filled_quantity", order.getFilledQuantity().toPlainString());
        m.put("trigger_price", order.getTriggerPrice() != null ? order.getTriggerPrice().toPlainString() : null);
        m.put("status", order.getStatus().name());
        m.put("sequence_time", order.getSequenceTime());
        m.put("display_quantity", order.getDisplayQuantity() != null ? order.getDisplayQuantity().toPlainString() : null);
        return m;
    }

    private MatchResultDTO parseMatchResult(String json, Order original) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        List<Trade> trades = new ArrayList<>();
        if (obj.has("trades")) {
            for (JsonElement e : obj.getAsJsonArray("trades")) {
                JsonObject t = e.getAsJsonObject();
                trades.add(Trade.builder()
                        .tradeId(t.get("trade_id").getAsLong())
                        .symbol(t.get("symbol").getAsString())
                        .price(new BigDecimal(t.get("price").getAsString()))
                        .quantity(new BigDecimal(t.get("quantity").getAsString()))
                        .takerOrderId(t.get("taker_order_id").getAsLong())
                        .makerOrderId(t.get("maker_order_id").getAsLong())
                        .takerUserId(t.get("taker_user_id").getAsLong())
                        .makerUserId(t.get("maker_user_id").getAsLong())
                        .tradeTime(t.get("trade_time").getAsLong())
                        .build());
            }
        }
        return MatchResultDTO.builder()
                .trades(trades)
                .affectedOrder(original)
                .build();
    }

    private MarketDepthDTO parseMarketDepth(String json, String symbol) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        List<DepthLevelDTO> bids = parseDepthLevels(obj.getAsJsonArray("bids"));
        List<DepthLevelDTO> asks = parseDepthLevels(obj.getAsJsonArray("asks"));
        long time = obj.has("generated_time") ? obj.get("generated_time").getAsLong() : System.currentTimeMillis();
        return MarketDepthDTO.builder()
                .symbol(symbol)
                .bids(bids)
                .asks(asks)
                .generatedTime(time)
                .build();
    }

    private List<DepthLevelDTO> parseDepthLevels(JsonArray arr) {
        if (arr == null) return List.of();
        List<DepthLevelDTO> result = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            result.add(DepthLevelDTO.builder()
                    .price(new BigDecimal(o.get("price").getAsString()))
                    .quantity(new BigDecimal(o.get("quantity").getAsString()))
                    .build());
        }
        return result;
    }
}
