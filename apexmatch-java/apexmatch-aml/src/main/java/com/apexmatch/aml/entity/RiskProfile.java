package com.apexmatch.aml.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RiskProfile {
    private Long profileId;
    private Long userId;
    private Integer riskScore;
    private String riskLevel;
    private BigDecimal dailyDepositLimit;
    private BigDecimal dailyWithdrawLimit;
    private BigDecimal monthlyTransactionLimit;
    private Boolean isBlacklisted;
    private String blacklistReason;
    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
