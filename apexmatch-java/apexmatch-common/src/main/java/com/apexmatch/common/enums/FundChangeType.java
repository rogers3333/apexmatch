package com.apexmatch.common.enums;

import lombok.Getter;

/**
 * 资金变动类型。
 *
 * @author luka
 * @since 2025-03-26
 */
@Getter
public enum FundChangeType {

    FREEZE,
    UNFREEZE,
    DEBIT,
    CREDIT,
    REALIZED_PNL,
    FUNDING_FEE,
    TRANSFER_IN,
    TRANSFER_OUT
}
