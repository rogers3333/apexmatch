package com.apexmatch.dex.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SwapRoute {
    private String fromToken;
    private String toToken;
    private BigDecimal amountIn;
    private BigDecimal amountOut;
    private List<String> path;
    private List<Long> protocolIds;
    private BigDecimal priceImpact;
    private BigDecimal totalFee;
}
