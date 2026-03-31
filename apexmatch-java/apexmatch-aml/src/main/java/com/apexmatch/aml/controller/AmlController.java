package com.apexmatch.aml.controller;

import com.apexmatch.aml.entity.*;
import com.apexmatch.aml.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/aml")
@RequiredArgsConstructor
public class AmlController {

    private final KycService kycService;
    private final AmlService amlService;

    @PostMapping("/kyc/submit")
    public KycRecord submitKyc(@RequestBody Map<String, String> req) {
        return kycService.submitKyc(Long.valueOf(req.get("userId")), req.get("kycLevel"),
                req.get("realName"), req.get("idType"), req.get("idNumber"), req.get("country"));
    }

    @PostMapping("/kyc/audit")
    public void auditKyc(@RequestBody Map<String, Object> req) {
        kycService.auditKyc(Long.valueOf(req.get("kycId").toString()),
                req.get("auditBy").toString(), Boolean.parseBoolean(req.get("approved").toString()),
                req.getOrDefault("rejectReason", "").toString());
    }

    @GetMapping("/kyc/{userId}")
    public KycRecord getKyc(@PathVariable Long userId) {
        return kycService.getKycByUserId(userId);
    }

    @GetMapping("/kyc/pending")
    public List<KycRecord> getPendingKyc() {
        return kycService.getPendingKyc();
    }

    @GetMapping("/risk/{userId}")
    public RiskProfile getRiskProfile(@PathVariable Long userId) {
        return amlService.getRiskProfile(userId);
    }

    @GetMapping("/alerts/open")
    public List<AmlAlert> getOpenAlerts() {
        return amlService.getOpenAlerts();
    }

    @PostMapping("/blacklist")
    public void blacklistUser(@RequestBody Map<String, String> req) {
        amlService.blacklistUser(Long.valueOf(req.get("userId")), req.get("reason"));
    }
}
