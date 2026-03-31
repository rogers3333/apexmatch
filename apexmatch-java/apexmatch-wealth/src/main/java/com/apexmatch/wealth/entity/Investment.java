package com.apexmatch.wealth.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Investment {
    private Long investmentId;
    private Long userId;
    private Long productId;
    private BigDecimal amount;
    private BigDecimal expectedReturn;
    private BigDecimal actualReturn;
    private String status;
    private LocalDateTime investedAt;
    private LocalDateTime maturityAt;
    private LocalDateTime redeemedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
