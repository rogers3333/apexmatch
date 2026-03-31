package com.apexmatch.settlement.funding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 资金费率服务测试
 * 覆盖测试用例：FU-001, FU-002, FU-003
 */
class FundingRateServiceTest {

    private FundingRateService fundingRateService;

    @BeforeEach
    void setUp() {
        fundingRateService = new FundingRateService();
    }

    @Test
    @DisplayName("FU-001: 资金费率计算准确性验证 - 正常情况")
    void testCalculateFundingRate_Normal() {
        // 溢价指数 0.01%, 利率 0.03%
        BigDecimal premiumIndex = new BigDecimal("0.0001");
        BigDecimal interestRate = new BigDecimal("0.0003");

        FundingRate rate = fundingRateService.calculateFundingRate(
                "BTC-USDT-PERP", premiumIndex, interestRate);

        // 资金费率 = 0.0001 + clamp(0.0003 - 0.0001, 0.0005, -0.0005)
        //          = 0.0001 + 0.0002 = 0.0003
        assertThat(rate.getFundingRate())
                .isEqualByComparingTo(new BigDecimal("0.0003"));
        assertThat(rate.getSymbol()).isEqualTo("BTC-USDT-PERP");
        assertThat(rate.getPremiumIndex()).isEqualByComparingTo(premiumIndex);
        assertThat(rate.getInterestRate()).isEqualByComparingTo(interestRate);
    }

    @Test
    @DisplayName("FU-001: 资金费率计算准确性验证 - Clamp 上限")
    void testCalculateFundingRate_ClampUpperBound() {
        // 溢价指数 0.01%, 利率 0.10% (差值 0.09% > 0.05%)
        BigDecimal premiumIndex = new BigDecimal("0.0001");
        BigDecimal interestRate = new BigDecimal("0.0010");

        FundingRate rate = fundingRateService.calculateFundingRate(
                "BTC-USDT-PERP", premiumIndex, interestRate);

        // 资金费率 = 0.0001 + clamp(0.0009, 0.0005, -0.0005)
        //          = 0.0001 + 0.0005 = 0.0006
        assertThat(rate.getFundingRate())
                .isEqualByComparingTo(new BigDecimal("0.0006"));
    }

    @Test
    @DisplayName("FU-001: 资金费率计算准确性验证 - Clamp 下限")
    void testCalculateFundingRate_ClampLowerBound() {
        // 溢价指数 0.10%, 利率 0.01% (差值 -0.09% < -0.05%)
        BigDecimal premiumIndex = new BigDecimal("0.0010");
        BigDecimal interestRate = new BigDecimal("0.0001");

        FundingRate rate = fundingRateService.calculateFundingRate(
                "BTC-USDT-PERP", premiumIndex, interestRate);

        // 资金费率 = 0.0010 + clamp(-0.0009, 0.0005, -0.0005)
        //          = 0.0010 + (-0.0005) = 0.0005
        assertThat(rate.getFundingRate())
                .isEqualByComparingTo(new BigDecimal("0.0005"));
    }

    @Test
    @DisplayName("FU-002: 资金费率正常结算验证 - 多头持仓支付费用")
    void testSettleFundingRate_LongPosition() {
        // 先计算资金费率
        fundingRateService.calculateFundingRate(
                "BTC-USDT-PERP",
                new BigDecimal("0.0001"),
                new BigDecimal("0.0003"));

        // 创建多头持仓
        List<FundingRateService.PositionInfo> positions = new ArrayList<>();
        FundingRateService.PositionInfo position = new FundingRateService.PositionInfo();
        position.setUserId(1001L);
        position.setPositionSide("LONG");
        position.setPositionQuantity(new BigDecimal("10"));
        position.setMarkPrice(new BigDecimal("50000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        // 执行结算
        fundingRateService.settleFundingRate("BTC-USDT-PERP", positions);

        // 验证结算记录
        List<FundingSettlement> settlements = fundingRateService.getUserSettlements(1001L, "BTC-USDT-PERP");
        assertThat(settlements).hasSize(1);

        FundingSettlement settlement = settlements.get(0);
        assertThat(settlement.getUserId()).isEqualTo(1001L);
        assertThat(settlement.getPositionSide()).isEqualTo("LONG");
        assertThat(settlement.getFundingRate()).isEqualByComparingTo(new BigDecimal("0.0003"));

        // 名义价值 = 50000 * 10 * 1 = 500000
        assertThat(settlement.getNotionalValue())
                .isEqualByComparingTo(new BigDecimal("500000"));

        // 资金费用 = 500000 * 0.0003 = 150 (多头支付，取负)
        assertThat(settlement.getFundingFee())
                .isEqualByComparingTo(new BigDecimal("-150"));
        assertThat(settlement.getStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("FU-002: 资金费率正常结算验证 - 空头持仓收取费用")
    void testSettleFundingRate_ShortPosition() {
        // 先计算资金费率
        fundingRateService.calculateFundingRate(
                "ETH-USDT-PERP",
                new BigDecimal("0.0002"),
                new BigDecimal("0.0001"));

        // 创建空头持仓
        List<FundingRateService.PositionInfo> positions = new ArrayList<>();
        FundingRateService.PositionInfo position = new FundingRateService.PositionInfo();
        position.setUserId(1002L);
        position.setPositionSide("SHORT");
        position.setPositionQuantity(new BigDecimal("100"));
        position.setMarkPrice(new BigDecimal("3000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        // 执行结算
        fundingRateService.settleFundingRate("ETH-USDT-PERP", positions);

        // 验证结算记录
        List<FundingSettlement> settlements = fundingRateService.getUserSettlements(1002L, "ETH-USDT-PERP");
        assertThat(settlements).hasSize(1);

        FundingSettlement settlement = settlements.get(0);
        // 资金费率 = 0.0002 + clamp(-0.0001, 0.0005, -0.0005) = 0.0001
        // 名义价值 = 3000 * 100 * 1 = 300000
        // 资金费用 = 300000 * 0.0001 = 30 (空头收取，不取负)
        assertThat(settlement.getFundingFee())
                .isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    @DisplayName("FU-003: 结算原子性验证 - 批量结算")
    void testSettleFundingRate_Atomicity() {
        // 先计算资金费率
        fundingRateService.calculateFundingRate(
                "BTC-USDT-PERP",
                new BigDecimal("0.0001"),
                new BigDecimal("0.0003"));

        // 创建多个持仓
        List<FundingRateService.PositionInfo> positions = new ArrayList<>();

        FundingRateService.PositionInfo position1 = new FundingRateService.PositionInfo();
        position1.setUserId(2001L);
        position1.setPositionSide("LONG");
        position1.setPositionQuantity(new BigDecimal("5"));
        position1.setMarkPrice(new BigDecimal("50000"));
        position1.setContractMultiplier(new BigDecimal("1"));
        positions.add(position1);

        FundingRateService.PositionInfo position2 = new FundingRateService.PositionInfo();
        position2.setUserId(2002L);
        position2.setPositionSide("SHORT");
        position2.setPositionQuantity(new BigDecimal("3"));
        position2.setMarkPrice(new BigDecimal("50000"));
        position2.setContractMultiplier(new BigDecimal("1"));
        positions.add(position2);

        FundingRateService.PositionInfo position3 = new FundingRateService.PositionInfo();
        position3.setUserId(2003L);
        position3.setPositionSide("LONG");
        position3.setPositionQuantity(new BigDecimal("2"));
        position3.setMarkPrice(new BigDecimal("50000"));
        position3.setContractMultiplier(new BigDecimal("1"));
        positions.add(position3);

        // 执行批量结算
        fundingRateService.settleFundingRate("BTC-USDT-PERP", positions);

        // 验证所有结算记录都已创建
        assertThat(fundingRateService.getUserSettlements(2001L, "BTC-USDT-PERP")).hasSize(1);
        assertThat(fundingRateService.getUserSettlements(2002L, "BTC-USDT-PERP")).hasSize(1);
        assertThat(fundingRateService.getUserSettlements(2003L, "BTC-USDT-PERP")).hasSize(1);

        // 验证费用计算正确
        FundingSettlement settlement1 = fundingRateService.getUserSettlements(2001L, "BTC-USDT-PERP").get(0);
        FundingSettlement settlement2 = fundingRateService.getUserSettlements(2002L, "BTC-USDT-PERP").get(0);

        // 多头支付费用（负数）
        assertThat(settlement1.getFundingFee()).isLessThan(BigDecimal.ZERO);
        // 空头收取费用（正数）
        assertThat(settlement2.getFundingFee()).isGreaterThan(BigDecimal.ZERO);
    }
}



