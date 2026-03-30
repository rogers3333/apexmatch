package com.apexmatch.risk.service;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.risk.service.impl.AdlServiceImpl;
import com.apexmatch.risk.service.impl.InsuranceFundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADL 自动减仓服务测试。
 *
 * @author luka
 * @since 2025-03-30
 */
class AdlServiceTest {

    private AdlService adlService;
    private InsuranceFundService insuranceFundService;
    private AccountService accountService;
    private PositionService positionService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl();
        positionService = new PositionServiceImpl();
        insuranceFundService = new InsuranceFundServiceImpl();
        adlService = new AdlServiceImpl(positionService, accountService, insuranceFundService);
    }

    @Test
    void testShouldTriggerAdl_WhenInsuranceFundLow() {
        insuranceFundService.collectLiquidationFee("USDT", new BigDecimal("500"), 1L, "BTC-USDT");
        assertTrue(adlService.shouldTriggerAdl("USDT"));
    }

    @Test
    void testShouldNotTriggerAdl_WhenInsuranceFundSufficient() {
        insuranceFundService.collectLiquidationFee("USDT", new BigDecimal("2000"), 1L, "BTC-USDT");
        assertFalse(adlService.shouldTriggerAdl("USDT"));
    }
}