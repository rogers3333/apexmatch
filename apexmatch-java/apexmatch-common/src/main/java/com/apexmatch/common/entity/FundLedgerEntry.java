package com.apexmatch.common.entity;

import com.apexmatch.common.enums.FundChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 资金流水条目（不可篡改、仅追加的业务语义由存储与审计层保证）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundLedgerEntry {

    private Long ledgerId;

    private Long userId;

    /**
     * SPOT / MARGIN 等账户类型编码
     */
    private String accountType;

    private String currency;

    private FundChangeType changeType;

    /**
     * 变动金额（正为增加，负为减少）
     */
    private BigDecimal amount;

    /**
     * 变动后余额快照
     */
    private BigDecimal balanceAfter;

    private Long refOrderId;

    private Long refTradeId;

    /**
     * 业务单号，便于对账
     */
    private String bizId;

    private long createdTime;
}
