package com.apexmatch.settlement.funding;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金费用结算记录
 */
@Data
public class FundingSettlement {
    private Long settlementId;
    private Long userId;
    private String symbol;
    private String positionSide;
    private BigDecimal positionQuantity;
    private BigDecimal markPrice;
    private BigDecimal notionalValue;
    private BigDecimal fundingRate;
    private BigDecimal fundingFee;
    private String status;
    private LocalDateTime settlementTime;
    private LocalDateTime createTime;
}
