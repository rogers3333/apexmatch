package com.apexmatch.common.exception;

import lombok.Getter;

/**
 * 统一错误码。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum ErrorCode {

    INVALID_ARGUMENT("40001", "参数非法"),
    DUPLICATE_CLIENT_ORDER("40901", "重复的客户端订单号"),
    INSUFFICIENT_BALANCE("40201", "余额不足"),
    INSUFFICIENT_MARGIN("40202", "保证金不足"),
    ORDER_NOT_FOUND("40401", "订单不存在"),
    ACCOUNT_NOT_FOUND("40402", "账户不存在"),
    POSITION_NOT_FOUND("40403", "持仓不存在"),
    RISK_CHECK_FAILED("40301", "风控校验未通过"),
    LIQUIDATION_TRIGGERED("40302", "触发强制平仓"),
    ENGINE_NOT_READY("50301", "撮合引擎未就绪");

    private final String code;

    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
