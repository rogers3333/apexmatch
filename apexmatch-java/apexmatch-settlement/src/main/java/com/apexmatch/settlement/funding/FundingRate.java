package com.apexmatch.settlement.funding;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金费率实体
 */
@Data
public class FundingRate {
    private Long id;
    private String symbol;
    private BigDecimal fundingRate;
    private BigDecimal premiumIndex;
    private BigDecimal interestRate;
    private Long fundingTime;
    private LocalDateTime nextFundingTime;
    private LocalDateTime createTime;
}
