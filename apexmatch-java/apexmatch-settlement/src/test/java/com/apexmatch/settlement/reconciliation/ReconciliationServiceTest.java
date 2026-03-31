package com.apexmatch.settlement.reconciliation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 对账服务测试
 * 覆盖测试用例：AC-003, AC-004
 */
class ReconciliationServiceTest {

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService();
    }

    @Test
    @DisplayName("AC-003: 资金流水完整性验证 - 对账成功")
    void testRealtimeReconciliation_Success() {
        // 创建账户信息
        List<ReconciliationService.AccountInfo> accounts = new ArrayList<>();
        ReconciliationService.AccountInfo account1 = new ReconciliationService.AccountInfo();
        account1.setUserId(5001L);
        account1.setBalance(new BigDecimal("100000"));
        accounts.add(account1);

        ReconciliationService.AccountInfo account2 = new ReconciliationService.AccountInfo();
        account2.setUserId(5002L);
        account2.setBalance(new BigDecimal("50000"));
        accounts.add(account2);

        ReconciliationService.AccountInfo account3 = new ReconciliationService.AccountInfo();
        account3.setUserId(5003L);
        account3.setBalance(new BigDecimal("75000"));
        accounts.add(account3);

        // 创建流水信息（总和应等于账户余额总和）
        List<ReconciliationService.FlowInfo> flows = new ArrayList<>();
        ReconciliationService.FlowInfo flow1 = new ReconciliationService.FlowInfo();
        flow1.setUserId(5001L);
        flow1.setAmount(new BigDecimal("100000"));
        flows.add(flow1);

        ReconciliationService.FlowInfo flow2 = new ReconciliationService.FlowInfo();
        flow2.setUserId(5002L);
        flow2.setAmount(new BigDecimal("50000"));
        flows.add(flow2);

        ReconciliationService.FlowInfo flow3 = new ReconciliationService.FlowInfo();
        flow3.setUserId(5003L);
        flow3.setAmount(new BigDecimal("75000"));
        flows.add(flow3);

        // 执行实时对账
        ReconciliationResult result = reconciliationService.realtimeReconciliation(accounts, flows);

        // 验证对账结果
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getReconciliationType()).isEqualTo("REALTIME");
        assertThat(result.getExpectedBalance()).isEqualByComparingTo(new BigDecimal("225000"));
        assertThat(result.getActualBalance()).isEqualByComparingTo(new BigDecimal("225000"));
        assertThat(result.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalAccounts()).isEqualTo(3);
        assertThat(result.getErrorAccounts()).isEqualTo(0);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("AC-003: 资金流水完整性验证 - 对账失败（余额不一致）")
    void testRealtimeReconciliation_Failure() {
        // 创建账户信息
        List<ReconciliationService.AccountInfo> accounts = new ArrayList<>();
        ReconciliationService.AccountInfo account1 = new ReconciliationService.AccountInfo();
        account1.setUserId(6001L);
        account1.setBalance(new BigDecimal("100000"));
        accounts.add(account1);

        ReconciliationService.AccountInfo account2 = new ReconciliationService.AccountInfo();
        account2.setUserId(6002L);
        account2.setBalance(new BigDecimal("50000"));
        accounts.add(account2);

        // 创建流水信息（总和与账户余额不一致）
        List<ReconciliationService.FlowInfo> flows = new ArrayList<>();
        ReconciliationService.FlowInfo flow1 = new ReconciliationService.FlowInfo();
        flow1.setUserId(6001L);
        flow1.setAmount(new BigDecimal("100000"));
        flows.add(flow1);

        ReconciliationService.FlowInfo flow2 = new ReconciliationService.FlowInfo();
        flow2.setUserId(6002L);
        flow2.setAmount(new BigDecimal("49000")); // 少了 1000
        flows.add(flow2);

        // 执行实时对账
        ReconciliationResult result = reconciliationService.realtimeReconciliation(accounts, flows);

        // 验证对账结果
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getExpectedBalance()).isEqualByComparingTo(new BigDecimal("149000"));
        assertThat(result.getActualBalance()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.getErrorAccounts()).isEqualTo(1);
        assertThat(result.getErrorMessage()).contains("余额总和与流水总和不一致");
    }

    @Test
    @DisplayName("AC-004: 日终全量对账验证 - 多账户对账成功")
    void testRealtimeReconciliation_MultipleAccounts() {
        // 创建大量账户信息（模拟日终全量对账）
        List<ReconciliationService.AccountInfo> accounts = new ArrayList<>();
        List<ReconciliationService.FlowInfo> flows = new ArrayList<>();

        BigDecimal totalBalance = BigDecimal.ZERO;
        for (long i = 7001L; i <= 7100L; i++) {
            BigDecimal balance = new BigDecimal(String.valueOf(i * 1000));
            totalBalance = totalBalance.add(balance);

            ReconciliationService.AccountInfo account = new ReconciliationService.AccountInfo();
            account.setUserId(i);
            account.setBalance(balance);
            accounts.add(account);

            ReconciliationService.FlowInfo flow = new ReconciliationService.FlowInfo();
            flow.setUserId(i);
            flow.setAmount(balance);
            flows.add(flow);
        }

        // 执行日终对账
        ReconciliationResult result = reconciliationService.realtimeReconciliation(accounts, flows);

        // 验证对账结果
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getTotalAccounts()).isEqualTo(100);
        assertThat(result.getErrorAccounts()).isEqualTo(0);
        assertThat(result.getDifference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("AC-004: 日终全量对账验证 - 检测到异常账户")
    void testRealtimeReconciliation_DetectAnomalies() {
        // 创建账户信息
        List<ReconciliationService.AccountInfo> accounts = new ArrayList<>();
        for (long i = 8001L; i <= 8010L; i++) {
            ReconciliationService.AccountInfo account = new ReconciliationService.AccountInfo();
            account.setUserId(i);
            account.setBalance(new BigDecimal("10000"));
            accounts.add(account);
        }

        // 创建流水信息（其中一个账户流水异常）
        List<ReconciliationService.FlowInfo> flows = new ArrayList<>();
        for (long i = 8001L; i <= 8010L; i++) {
            ReconciliationService.FlowInfo flow = new ReconciliationService.FlowInfo();
            flow.setUserId(i);
            if (i == 8005L) {
                flow.setAmount(new BigDecimal("9500")); // 异常账户
            } else {
                flow.setAmount(new BigDecimal("10000"));
            }
            flows.add(flow);
        }

        // 执行对账
        ReconciliationResult result = reconciliationService.realtimeReconciliation(accounts, flows);

        // 验证对账结果
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getDifference()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.getErrorMessage()).isNotNull();
    }
}



