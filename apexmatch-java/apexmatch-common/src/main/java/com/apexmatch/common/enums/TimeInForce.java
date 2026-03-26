package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 订单有效期 / 成交策略（FOK、IOC、普通）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum TimeInForce {

    /**
     * 成交为止，允许部分挂单
     */
    GTC,

    /**
     * 立即成交剩余取消（IOC）
     */
    IOC,

    /**
     * 全部立即成交否则取消（FOK）
     */
    FOK
}
