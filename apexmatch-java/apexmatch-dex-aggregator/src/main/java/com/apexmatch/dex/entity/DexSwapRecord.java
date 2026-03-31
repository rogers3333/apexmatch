package com.apexmatch.dex.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DexSwapRecord {
    private Long swapId;
    private Long userId;
    private String chainCode;
    private Long protocolId;
    private String fromToken;
    private String toToken;
    private BigDecimal amountIn;
    private BigDecimal amountOut;
    private String txHash;
    private String status;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}
