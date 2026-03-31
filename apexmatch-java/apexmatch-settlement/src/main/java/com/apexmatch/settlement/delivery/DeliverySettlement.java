package com.apexmatch.settlement.delivery;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交割结算记录
 */
@Data
public class DeliverySettlement {
    private Long settlementId;
    private Long userId;
    private String symbol;
    private String positionSide;
    private BigDecimal positionQuantity;
    private BigDecimal entryPrice;
    private BigDecimal deliveryPrice;
    private BigDecimal realizedPnl;
    private BigDecimal deliveryFee;
    private String status;
    private LocalDateTime deliveryTime;
    private LocalDateTime createTime;
}
