package com.apexmatch.settlement.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 对账服务
 * 负责实时对账、日终对账、资金流水审计
 */
@Slf4j
@Service
public class ReconciliationService {

    private final List<ReconciliationResult> results = new ArrayList<>();

    /**
     * 实时对账
     * 核对账户余额总和与流水总和
     */
    public ReconciliationResult realtimeReconciliation(List<AccountInfo> accounts, List<FlowInfo> flows) {
        log.info("开始实时对账: accounts={}, flows={}", accounts.size(), flows.size());

        BigDecimal totalBalance = accounts.stream()
                .map(AccountInfo::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFlow = flows.stream()
                .map(FlowInfo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal difference = totalBalance.subtract(totalFlow);

        ReconciliationResult result = new ReconciliationResult();
        result.setReconciliationType("REALTIME");
        result.setReconciliationTime(LocalDateTime.now());
        result.setSuccess(difference.compareTo(BigDecimal.ZERO) == 0);
        result.setExpectedBalance(totalFlow);
        result.setActualBalance(totalBalance);
        result.setDifference(difference);
        result.setTotalAccounts(accounts.size());
        result.setErrorAccounts(difference.compareTo(BigDecimal.ZERO) == 0 ? 0 : 1);
        result.setCreateTime(LocalDateTime.now());

        if (!result.getSuccess()) {
            result.setErrorMessage("余额总和与流水总和不一致，差额: " + difference);
            log.error("实时对账失败: {}", result.getErrorMessage());
        } else {
            log.info("实时对账成功");
        }

        results.add(result);
        return result;
    }
