package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 持仓方向（双向模式下使用）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum PositionSide {

    /**
     * 多头
     */
    LONG,

    /**
     * 空头
     */
    SHORT
}
