package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 保证金模式：逐仓 / 全仓。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum MarginMode {

    /**
     * 逐仓：单笔仓位独立保证金
     */
    ISOLATED,

    /**
     * 全仓：账户维度的共享保证金
     */
    CROSS
}
