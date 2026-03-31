package com.apexmatch.agent.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Agent {
    private Long agentId;
    private Long userId;
    private String agentCode;
    private String agentLevel;
    private BigDecimal commissionRate;
    private Long parentAgentId;
    private Integer totalReferrals;
    private BigDecimal totalCommission;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
