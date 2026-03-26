package com.apexmatch.common.exception;

import lombok.Getter;

/**
 * 业务运行时异常，供全局异常处理器转换 HTTP 状态与错误体。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public class ApexMatchException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApexMatchException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApexMatchException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ApexMatchException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
