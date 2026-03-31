package com.apexmatch.aml.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AmlAlert {
    private Long alertId;
    private Long userId;
    private String alertType;
    private String severity;
    private String description;
    private String relatedTxHash;
    private String status;
    private String handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
