package com.apexmatch.settlement.reconciliation;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账结果
 */
@Data
public class ReconciliationResult {
    private String reconciliationType;
    private LocalDateTime reconciliationTime;
    private Boolean success;
    private BigDecimal expectedBalance;
    private BigDecimal actualBalance;
    private BigDecimal difference;
    private String errorMessage;
    private Integer totalAccounts;
    private Integer errorAccounts;
    private LocalDateTime createTime;
}
