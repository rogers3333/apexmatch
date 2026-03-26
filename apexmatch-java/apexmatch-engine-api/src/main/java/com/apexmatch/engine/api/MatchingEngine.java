package com.apexmatch.engine.api;

import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.dto.EngineInitOptions;
import com.apexmatch.engine.api.dto.MarketDepthDTO;
import com.apexmatch.engine.api.dto.MatchResultDTO;

import java.util.Optional;

/**
 * 撮合引擎标准接口。Java 实现与 Rust FFI 适配层均需实现本接口，供业务层按配置注入。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface MatchingEngine {

    /**
     * 初始化指定交易对的撮合状态（订单簿、序列等）。
     *
     * @param symbol  交易对
     * @param options 可选参数（ Wal 路径、快照目录等），允许为 null
     */
    void init(String symbol, EngineInitOptions options);

    /**
     * 提交订单并尝试撮合。
     *
     * @param order 订单快照
     * @return 撮合结果（成交列表、剩余挂单信息等）
     */
    MatchResultDTO submitOrder(Order order);

    /**
     * 撤销订单；要求平均 O(1) 索引撤单。
     *
     * @param symbol  交易对分片键
     * @param orderId 系统订单号
     * @return 是否撤销成功（不存在返回 empty）
     */
    Optional<Boolean> cancelOrder(String symbol, long orderId);

    /**
     * 查询指定档位的买卖盘口。
     *
     * @param symbol 交易对
     * @param levels 深度档位数
     * @return 聚合后的盘口快照
     */
    MarketDepthDTO getMarketDepth(String symbol, int levels);
}
