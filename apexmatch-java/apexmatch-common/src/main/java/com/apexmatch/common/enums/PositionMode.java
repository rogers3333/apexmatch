package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 持仓模式：单向 / 双向（对冲）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum PositionMode {

    /**
     * 单向持仓：同一合约净头寸
     */
    ONE_WAY,

    /**
     * 双向持仓：可同时持有多仓与空仓
     */
    HEDGE
}
