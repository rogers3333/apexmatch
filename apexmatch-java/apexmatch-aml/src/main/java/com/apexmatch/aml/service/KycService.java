package com.apexmatch.aml.service;

import com.apexmatch.aml.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class KycService {

    private final Map<Long, KycRecord> kycRecords = new ConcurrentHashMap<>();

    public KycRecord submitKyc(Long userId, String kycLevel, String realName, String idType,
                               String idNumber, String country) {
        KycRecord record = new KycRecord();
        record.setKycId(System.currentTimeMillis());
        record.setUserId(userId);
        record.setKycLevel(kycLevel);
        record.setRealName(realName);
        record.setIdType(idType);
        record.setIdNumber(idNumber);
        record.setCountry(country);
        record.setStatus("PENDING");
        record.setSubmittedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        kycRecords.put(record.getKycId(), record);
        log.info("提交 KYC: userId={}, level={}, name={}", userId, kycLevel, realName);
        return record;
    }

    public void auditKyc(Long kycId, String auditBy, boolean approved, String rejectReason) {
        KycRecord record = kycRecords.get(kycId);
        if (record == null) return;
        record.setAuditBy(auditBy);
        record.setAuditAt(LocalDateTime.now());
        if (approved) {
            record.setStatus("APPROVED");
        } else {
            record.setStatus("REJECTED");
            record.setRejectReason(rejectReason);
        }
        record.setUpdatedAt(LocalDateTime.now());
        log.info("审核 KYC: kycId={}, approved={}", kycId, approved);
    }

    public KycRecord getKycByUserId(Long userId) {
        return kycRecords.values().stream()
                .filter(k -> k.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public List<KycRecord> getPendingKyc() {
        return kycRecords.values().stream()
                .filter(k -> "PENDING".equals(k.getStatus()))
                .toList();
    }
}
