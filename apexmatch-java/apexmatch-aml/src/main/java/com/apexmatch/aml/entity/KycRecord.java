package com.apexmatch.aml.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class KycRecord {
    private Long kycId;
    private Long userId;
    private String kycLevel;
    private String realName;
    private String idType;
    private String idNumber;
    private String country;
    private String idCardFront;
    private String idCardBack;
    private String facePhoto;
    private String status;
    private String rejectReason;
    private String auditBy;
    private LocalDateTime auditAt;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
