package com.apexmatch.dex.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LiquidityPool {
    private Long poolId;
    private Long protocolId;
    private String chainCode;
    private String poolAddress;
    private String token0;
    private String token1;
    private BigDecimal reserve0;
    private BigDecimal reserve1;
    private BigDecimal liquidity;
}
