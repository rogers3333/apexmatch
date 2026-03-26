package com.apexmatch.ha.tcc;

/**
 * TCC 事务状态。
 *
 * @author luka
 * @since 2025-03-26
 */
public enum TccStatus {
    TRYING,
    CONFIRMING,
    CANCELLING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
