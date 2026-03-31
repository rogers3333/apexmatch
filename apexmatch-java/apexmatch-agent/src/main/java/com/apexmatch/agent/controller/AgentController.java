package com.apexmatch.agent.controller;

import com.apexmatch.agent.entity.*;
import com.apexmatch.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/create")
    public Agent createAgent(@RequestBody Map<String, Object> req) {
        return agentService.createAgent(
                Long.valueOf(req.get("userId").toString()),
                req.get("agentLevel").toString(),
                new java.math.BigDecimal(req.get("commissionRate").toString()),
                req.containsKey("parentAgentId") ? Long.valueOf(req.get("parentAgentId").toString()) : null
        );
    }

    @PostMapping("/referral/register")
    public Referral registerReferral(@RequestBody Map<String, Object> req) {
        return agentService.registerReferral(
                req.get("agentCode").toString(),
                Long.valueOf(req.get("referredUserId").toString())
        );
    }

    @PostMapping("/commission/calculate")
    public Commission calculateCommission(@RequestBody Map<String, Object> req) {
        return agentService.calculateCommission(
                Long.valueOf(req.get("referredUserId").toString()),
                req.get("sourceType").toString(),
                req.get("sourceId").toString(),
                new java.math.BigDecimal(req.get("baseAmount").toString())
        );
    }

    @PostMapping("/commission/settle/{commissionId}")
    public void settleCommission(@PathVariable Long commissionId) {
        agentService.settleCommission(commissionId);
    }

    @GetMapping("/{agentId}")
    public Agent getAgent(@PathVariable Long agentId) {
        return agentService.getAgent(agentId);
    }

    @GetMapping("/{agentId}/commissions")
    public List<Commission> getCommissions(@PathVariable Long agentId) {
        return agentService.getAgentCommissions(agentId);
    }

    @GetMapping("/{agentId}/referrals")
    public List<Referral> getReferrals(@PathVariable Long agentId) {
        return agentService.getAgentReferrals(agentId);
    }
}
