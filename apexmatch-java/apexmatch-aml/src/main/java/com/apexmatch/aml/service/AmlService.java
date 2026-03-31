package com.apexmatch.aml.service;

import com.apexmatch.aml.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AmlService {

    private final Map<Long, RiskProfile> riskProfiles = new ConcurrentHashMap<>();
    private final List<AmlAlert> alerts = new ArrayList<>();

    public RiskProfile createRiskProfile(Long userId) {
        RiskProfile profile = new RiskProfile();
        profile.setProfileId(System.currentTimeMillis());
        profile.setUserId(userId);
        profile.setRiskScore(0);
        profile.setRiskLevel("LOW");
        profile.setDailyDepositLimit(new BigDecimal("100000"));
        profile.setDailyWithdrawLimit(new BigDecimal("50000"));
        profile.setMonthlyTransactionLimit(new BigDecimal("1000000"));
        profile.setIsBlacklisted(false);
        profile.setLastEvaluatedAt(LocalDateTime.now());
        profile.setCreatedAt(LocalDateTime.now());
        riskProfiles.put(userId, profile);
        return profile;
    }

    public void evaluateRisk(Long userId, BigDecimal transactionAmount, String txType) {
        RiskProfile profile = riskProfiles.computeIfAbsent(userId, this::createRiskProfile);
        int riskScore = profile.getRiskScore();

        if (transactionAmount.compareTo(new BigDecimal("10000")) > 0) {
            riskScore += 10;
            createAlert(userId, "LARGE_TRANSACTION", "MEDIUM",
                    "大额交易: " + transactionAmount, null);
        }

        if (riskScore > 80) {
            profile.setRiskLevel("HIGH");
            profile.setDailyWithdrawLimit(new BigDecimal("10000"));
        } else if (riskScore > 50) {
            profile.setRiskLevel("MEDIUM");
        } else {
            profile.setRiskLevel("LOW");
        }

        profile.setRiskScore(riskScore);
        profile.setLastEvaluatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        log.info("风险评估: userId={}, score={}, level={}", userId, riskScore, profile.getRiskLevel());
    }

    public AmlAlert createAlert(Long userId, String alertType, String severity,
                                String description, String relatedTxHash) {
        AmlAlert alert = new AmlAlert();
        alert.setAlertId(System.currentTimeMillis());
        alert.setUserId(userId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setDescription(description);
        alert.setRelatedTxHash(relatedTxHash);
        alert.setStatus("OPEN");
        alert.setCreatedAt(LocalDateTime.now());
        alerts.add(alert);
        log.warn("AML 告警: userId={}, type={}, severity={}", userId, alertType, severity);
        return alert;
    }

    public void blacklistUser(Long userId, String reason) {
        RiskProfile profile = riskProfiles.get(userId);
        if (profile != null) {
            profile.setIsBlacklisted(true);
            profile.setBlacklistReason(reason);
            profile.setUpdatedAt(LocalDateTime.now());
            log.warn("用户加入黑名单: userId={}, reason={}", userId, reason);
        }
    }

    public RiskProfile getRiskProfile(Long userId) {
        return riskProfiles.get(userId);
    }

    public List<AmlAlert> getOpenAlerts() {
        return alerts.stream()
                .filter(a -> "OPEN".equals(a.getStatus()))
                .toList();
    }
}
