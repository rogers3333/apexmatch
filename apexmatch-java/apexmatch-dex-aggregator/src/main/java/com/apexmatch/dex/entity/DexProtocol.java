package com.apexmatch.dex.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DexProtocol {
    private Long protocolId;
    private String protocolName;
    private String chainCode;
    private String routerAddress;
    private String factoryAddress;
    private BigDecimal feeRate;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
