package com.apexmatch.settlement.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 交割结算服务测试
 * 覆盖测试用例：FU-004
 */
class DeliverySettlementServiceTest {

    private DeliverySettlementService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliverySettlementService();
    }

    @Test
    @DisplayName("FU-004: 交割合约到期结算验证 - 多头盈利")
    void testExecuteDelivery_LongProfit() {
        // 创建多头持仓（开仓价 50000，交割价 52000，盈利）
        List<DeliverySettlementService.PositionInfo> positions = new ArrayList<>();
        DeliverySettlementService.PositionInfo position = new DeliverySettlementService.PositionInfo();
        position.setUserId(3001L);
        position.setPositionSide("LONG");
        position.setPositionQuantity(new BigDecimal("10"));
        position.setEntryPrice(new BigDecimal("50000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        BigDecimal deliveryPrice = new BigDecimal("52000");

        // 执行交割
        deliveryService.executeDelivery("BTC-USDT-0331", deliveryPrice, positions);

        // 验证交割记录
        List<DeliverySettlement> settlements = deliveryService.getUserDeliveries(3001L);
        assertThat(settlements).hasSize(1);

        DeliverySettlement settlement = settlements.get(0);
        assertThat(settlement.getUserId()).isEqualTo(3001L);
        assertThat(settlement.getPositionSide()).isEqualTo("LONG");
        assertThat(settlement.getDeliveryPrice()).isEqualByComparingTo(deliveryPrice);

        // 盈亏 = (52000 - 50000) * 10 * 1 = 20000
        // 交割费用 = 52000 * 10 * 1 * 0.0005 = 260
        // 已实现盈亏 = 20000 - 260 = 19740
        assertThat(settlement.getRealizedPnl())
                .isEqualByComparingTo(new BigDecimal("19740"));
        assertThat(settlement.getDeliveryFee())
                .isEqualByComparingTo(new BigDecimal("260"));
        assertThat(settlement.getStatus()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("FU-004: 交割合约到期结算验证 - 多头亏损")
    void testExecuteDelivery_LongLoss() {
        // 创建多头持仓（开仓价 50000，交割价 48000，亏损）
        List<DeliverySettlementService.PositionInfo> positions = new ArrayList<>();
        DeliverySettlementService.PositionInfo position = new DeliverySettlementService.PositionInfo();
        position.setUserId(3002L);
        position.setPositionSide("LONG");
        position.setPositionQuantity(new BigDecimal("5"));
        position.setEntryPrice(new BigDecimal("50000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        BigDecimal deliveryPrice = new BigDecimal("48000");

        // 执行交割
        deliveryService.executeDelivery("BTC-USDT-0331", deliveryPrice, positions);

        // 验证交割记录
        List<DeliverySettlement> settlements = deliveryService.getUserDeliveries(3002L);
        assertThat(settlements).hasSize(1);

        DeliverySettlement settlement = settlements.get(0);
        // 盈亏 = (48000 - 50000) * 5 * 1 = -10000
        // 交割费用 = 48000 * 5 * 1 * 0.0005 = 120
        // 已实现盈亏 = -10000 - 120 = -10120
        assertThat(settlement.getRealizedPnl())
                .isEqualByComparingTo(new BigDecimal("-10120"));
        assertThat(settlement.getDeliveryFee())
                .isEqualByComparingTo(new BigDecimal("120"));
    }

    @Test
    @DisplayName("FU-004: 交割合约到期结算验证 - 空头盈利")
    void testExecuteDelivery_ShortProfit() {
        // 创建空头持仓（开仓价 50000，交割价 48000，盈利）
        List<DeliverySettlementService.PositionInfo> positions = new ArrayList<>();
        DeliverySettlementService.PositionInfo position = new DeliverySettlementService.PositionInfo();
        position.setUserId(3003L);
        position.setPositionSide("SHORT");
        position.setPositionQuantity(new BigDecimal("8"));
        position.setEntryPrice(new BigDecimal("50000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        BigDecimal deliveryPrice = new BigDecimal("48000");

        // 执行交割
        deliveryService.executeDelivery("BTC-USDT-0331", deliveryPrice, positions);

        // 验证交割记录
        List<DeliverySettlement> settlements = deliveryService.getUserDeliveries(3003L);
        assertThat(settlements).hasSize(1);

        DeliverySettlement settlement = settlements.get(0);
        // 盈亏 = (50000 - 48000) * 8 * 1 = 16000
        // 交割费用 = 48000 * 8 * 1 * 0.0005 = 192
        // 已实现盈亏 = 16000 - 192 = 15808
        assertThat(settlement.getRealizedPnl())
                .isEqualByComparingTo(new BigDecimal("15808"));
    }

    @Test
    @DisplayName("FU-004: 交割合约到期结算验证 - 空头亏损")
    void testExecuteDelivery_ShortLoss() {
        // 创建空头持仓（开仓价 50000，交割价 52000，亏损）
        List<DeliverySettlementService.PositionInfo> positions = new ArrayList<>();
        DeliverySettlementService.PositionInfo position = new DeliverySettlementService.PositionInfo();
        position.setUserId(3004L);
        position.setPositionSide("SHORT");
        position.setPositionQuantity(new BigDecimal("3"));
        position.setEntryPrice(new BigDecimal("50000"));
        position.setContractMultiplier(new BigDecimal("1"));
        positions.add(position);

        BigDecimal deliveryPrice = new BigDecimal("52000");

        // 执行交割
        deliveryService.executeDelivery("BTC-USDT-0331", deliveryPrice, positions);

        // 验证交割记录
        List<DeliverySettlement> settlements = deliveryService.getUserDeliveries(3004L);
        assertThat(settlements).hasSize(1);

        DeliverySettlement settlement = settlements.get(0);
        // 盈亏 = (50000 - 52000) * 3 * 1 = -6000
        // 交割费用 = 52000 * 3 * 1 * 0.0005 = 78
        // 已实现盈亏 = -6000 - 78 = -6078
        assertThat(settlement.getRealizedPnl())
                .isEqualByComparingTo(new BigDecimal("-6078"));
    }

    @Test
    @DisplayName("FU-004: 交割合约到期结算验证 - 批量交割原子性")
    void testExecuteDelivery_BatchAtomicity() {
        // 创建多个持仓
        List<DeliverySettlementService.PositionInfo> positions = new ArrayList<>();

        DeliverySettlementService.PositionInfo position1 = new DeliverySettlementService.PositionInfo();
        position1.setUserId(4001L);
        position1.setPositionSide("LONG");
        position1.setPositionQuantity(new BigDecimal("5"));
        position1.setEntryPrice(new BigDecimal("50000"));
        position1.setContractMultiplier(new BigDecimal("1"));
        positions.add(position1);

        DeliverySettlementService.PositionInfo position2 = new DeliverySettlementService.PositionInfo();
        position2.setUserId(4002L);
        position2.setPositionSide("SHORT");
        position2.setPositionQuantity(new BigDecimal("3"));
        position2.setEntryPrice(new BigDecimal("48000"));
        position2.setContractMultiplier(new BigDecimal("1"));
        positions.add(position2);

        DeliverySettlementService.PositionInfo position3 = new DeliverySettlementService.PositionInfo();
        position3.setUserId(4003L);
        position3.setPositionSide("LONG");
        position3.setPositionQuantity(new BigDecimal("2"));
        position3.setEntryPrice(new BigDecimal("51000"));
        position3.setContractMultiplier(new BigDecimal("1"));
        positions.add(position3);

        BigDecimal deliveryPrice = new BigDecimal("50000");

        // 执行批量交割
        deliveryService.executeDelivery("BTC-USDT-0331", deliveryPrice, positions);

        // 验证所有交割记录都已创建
        assertThat(deliveryService.getUserDeliveries(4001L)).hasSize(1);
        assertThat(deliveryService.getUserDeliveries(4002L)).hasSize(1);
        assertThat(deliveryService.getUserDeliveries(4003L)).hasSize(1);

        // 验证盈亏计算正确
        DeliverySettlement settlement1 = deliveryService.getUserDeliveries(4001L).get(0);
        DeliverySettlement settlement2 = deliveryService.getUserDeliveries(4002L).get(0);
        DeliverySettlement settlement3 = deliveryService.getUserDeliveries(4003L).get(0);

        // 用户 4001: 多头，开仓价 50000，交割价 50000，盈亏 0，只扣交割费
        assertThat(settlement1.getRealizedPnl()).isLessThan(BigDecimal.ZERO);

        // 用户 4002: 空头，开仓价 48000，交割价 50000，亏损
        assertThat(settlement2.getRealizedPnl()).isLessThan(BigDecimal.ZERO);

        // 用户 4003: 多头，开仓价 51000，交割价 50000，亏损
        assertThat(settlement3.getRealizedPnl()).isLessThan(BigDecimal.ZERO);
    }
}



