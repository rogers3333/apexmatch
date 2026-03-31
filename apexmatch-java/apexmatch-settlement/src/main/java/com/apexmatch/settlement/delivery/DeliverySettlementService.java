package com.apexmatch.settlement.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 交割结算服务
 * 负责交割合约到期结算、交割处理
 */
@Slf4j
@Service
public class DeliverySettlementService {

    private final List<DeliverySettlement> settlements = new ArrayList<>();

    /**
     * 执行交割结算（原子化）
     * 持仓自动平仓，计算已实现盈亏，扣除交割费用
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeDelivery(String symbol, BigDecimal deliveryPrice, List<PositionInfo> positions) {
        log.info("开始交割结算: symbol={}, deliveryPrice={}, positions={}",
                symbol, deliveryPrice, positions.size());

        List<DeliverySettlement> batchSettlements = new ArrayList<>();

        for (PositionInfo position : positions) {
            BigDecimal pnl;
            if ("LONG".equals(position.getPositionSide())) {
                pnl = deliveryPrice.subtract(position.getEntryPrice())
                        .multiply(position.getPositionQuantity())
                        .multiply(position.getContractMultiplier());
            } else {
                pnl = position.getEntryPrice().subtract(deliveryPrice)
                        .multiply(position.getPositionQuantity())
                        .multiply(position.getContractMultiplier());
            }

            BigDecimal deliveryFee = deliveryPrice
                    .multiply(position.getPositionQuantity())
                    .multiply(position.getContractMultiplier())
                    .multiply(new BigDecimal("0.0005"))
                    .setScale(8, RoundingMode.HALF_UP);

            BigDecimal realizedPnl = pnl.subtract(deliveryFee);

            DeliverySettlement settlement = new DeliverySettlement();
            settlement.setSettlementId(System.currentTimeMillis() + position.getUserId());
            settlement.setUserId(position.getUserId());
            settlement.setSymbol(symbol);
            settlement.setPositionSide(position.getPositionSide());
            settlement.setPositionQuantity(position.getPositionQuantity());
            settlement.setEntryPrice(position.getEntryPrice());
            settlement.setDeliveryPrice(deliveryPrice);
            settlement.setRealizedPnl(realizedPnl);
            settlement.setDeliveryFee(deliveryFee);
            settlement.setStatus("SETTLED");
            settlement.setDeliveryTime(LocalDateTime.now());
            settlement.setCreateTime(LocalDateTime.now());

            batchSettlements.add(settlement);
        }

        settlements.addAll(batchSettlements);
        log.info("交割结算完成: symbol={}, count={}", symbol, batchSettlements.size());
    }

    /**
     * 查询用户交割历史
     */
    public List<DeliverySettlement> getUserDeliveries(Long userId) {
        return settlements.stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList();
    }

    /**
     * 持仓信息（用于交割）
     */
    @lombok.Data
    public static class PositionInfo {
        private Long userId;
        private String positionSide;
        private BigDecimal positionQuantity;
        private BigDecimal entryPrice;
        private BigDecimal contractMultiplier;
    }
}
