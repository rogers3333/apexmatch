package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 订单类型（与需求文档中的订单类型一一对应）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum OrderType {

    /**
     * 限价单
     */
    LIMIT,

    /**
     * 市价单
     */
    MARKET,

    /**
     * 止损限价单
     */
    STOP_LIMIT,

    /**
     * 止损市价单
     */
    STOP_MARKET,

    /**
     * 冰山单（后续撮合实现使用）
     */
    ICEBERG
}
