package com.apexmatch.wealth.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WealthProduct {
    private Long productId;
    private String productName;
    private String productType;
    private String currencyCode;
    private BigDecimal annualRate;
    private Integer lockDays;
    private BigDecimal minInvestAmount;
    private BigDecimal maxInvestAmount;
    private BigDecimal totalQuota;
    private BigDecimal remainingQuota;
    private Boolean isActive;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
