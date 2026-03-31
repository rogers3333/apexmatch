package com.apexmatch.wealth.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProfitDistribution {
    private Long distributionId;
    private Long investmentId;
    private Long userId;
    private String currencyCode;
    private BigDecimal profitAmount;
    private String status;
    private LocalDateTime distributedAt;
    private LocalDateTime createdAt;
}
