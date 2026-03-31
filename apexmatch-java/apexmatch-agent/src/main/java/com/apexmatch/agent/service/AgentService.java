package com.apexmatch.agent.service;

import com.apexmatch.agent.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentService {

    private final Map<Long, Agent> agents = new ConcurrentHashMap<>();
    private final Map<String, Agent> agentsByCode = new ConcurrentHashMap<>();
    private final List<Referral> referrals = new ArrayList<>();
    private final List<Commission> commissions = new ArrayList<>();

    public Agent createAgent(Long userId, String agentLevel, BigDecimal commissionRate, Long parentAgentId) {
        Agent agent = new Agent();
        agent.setAgentId(System.currentTimeMillis());
        agent.setUserId(userId);
        agent.setAgentCode("AG" + userId);
        agent.setAgentLevel(agentLevel);
        agent.setCommissionRate(commissionRate);
        agent.setParentAgentId(parentAgentId);
        agent.setTotalReferrals(0);
        agent.setTotalCommission(BigDecimal.ZERO);
        agent.setIsActive(true);
        agent.setCreatedAt(LocalDateTime.now());
        agents.put(agent.getAgentId(), agent);
        agentsByCode.put(agent.getAgentCode(), agent);
        log.info("创建代理: agentId={}, code={}, level={}", agent.getAgentId(), agent.getAgentCode(), agentLevel);
        return agent;
    }

    public Referral registerReferral(String agentCode, Long referredUserId) {
        Agent agent = agentsByCode.get(agentCode);
        if (agent == null || !agent.getIsActive()) {
            throw new RuntimeException("代理不存在或已停用");
        }

        Referral referral = new Referral();
        referral.setReferralId(System.currentTimeMillis());
        referral.setAgentId(agent.getAgentId());
        referral.setReferredUserId(referredUserId);
        referral.setReferralCode(agentCode);
        referral.setRegisteredAt(LocalDateTime.now());
        referral.setCreatedAt(LocalDateTime.now());
        referrals.add(referral);

        agent.setTotalReferrals(agent.getTotalReferrals() + 1);
        agent.setUpdatedAt(LocalDateTime.now());
        log.info("注册推荐: agentId={}, referredUserId={}", agent.getAgentId(), referredUserId);
        return referral;
    }

    public Commission calculateCommission(Long referredUserId, String sourceType, String sourceId,
                                          BigDecimal baseAmount) {
        Referral referral = referrals.stream()
                .filter(r -> r.getReferredUserId().equals(referredUserId))
                .findFirst()
                .orElse(null);
        if (referral == null) return null;

        Agent agent = agents.get(referral.getAgentId());
        if (agent == null || !agent.getIsActive()) return null;

        Commission commission = new Commission();
        commission.setCommissionId(System.currentTimeMillis());
        commission.setAgentId(agent.getAgentId());
        commission.setReferredUserId(referredUserId);
        commission.setSourceType(sourceType);
        commission.setSourceId(sourceId);
        commission.setBaseAmount(baseAmount);
        commission.setCommissionRate(agent.getCommissionRate());
        commission.setCommissionAmount(baseAmount.multiply(agent.getCommissionRate()));
        commission.setStatus("PENDING");
        commission.setCreatedAt(LocalDateTime.now());
        commissions.add(commission);

        agent.setTotalCommission(agent.getTotalCommission().add(commission.getCommissionAmount()));
        agent.setUpdatedAt(LocalDateTime.now());
        log.info("计算佣金: agentId={}, amount={}", agent.getAgentId(), commission.getCommissionAmount());
        return commission;
    }

    public void settleCommission(Long commissionId) {
        Commission commission = commissions.stream()
                .filter(c -> c.getCommissionId().equals(commissionId))
                .findFirst()
                .orElse(null);
        if (commission != null && "PENDING".equals(commission.getStatus())) {
            commission.setStatus("SETTLED");
            commission.setSettledAt(LocalDateTime.now());
            log.info("结算佣金: commissionId={}, amount={}", commissionId, commission.getCommissionAmount());
        }
    }

    public Agent getAgent(Long agentId) {
        return agents.get(agentId);
    }

    public List<Commission> getAgentCommissions(Long agentId) {
        return commissions.stream()
                .filter(c -> c.getAgentId().equals(agentId))
                .toList();
    }

    public List<Referral> getAgentReferrals(Long agentId) {
        return referrals.stream()
                .filter(r -> r.getAgentId().equals(agentId))
                .toList();
    }
}
