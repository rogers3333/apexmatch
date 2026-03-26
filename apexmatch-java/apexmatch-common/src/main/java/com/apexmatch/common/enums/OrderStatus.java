package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 订单生命周期状态。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum OrderStatus {

    /**
     * 新建（尚未进入撮合队列或未激活的止损单）
     */
    NEW,

    /**
     * 部分成交
     */
    PARTIALLY_FILLED,

    /**
     * 全部成交
     */
    FILLED,

    /**
     * 已撤销
     */
    CANCELED,

    /**
     * 已拒绝
     */
    REJECTED,

    /**
     * 已过期
     */
    EXPIRED
}
