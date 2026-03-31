package com.apexmatch.agent.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Commission {
    private Long commissionId;
    private Long agentId;
    private Long referredUserId;
    private String sourceType;
    private String sourceId;
    private BigDecimal baseAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private String status;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
}
