package com.apexmatch.risk.service.impl;

import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.risk.service.SlTpMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * 止损/止盈监控服务实现。
 *
 * 逻辑：
 * 1. 获取用户持仓
 * 2. 检查持仓是否设置了止损价/止盈价
 * 3. 根据最新价格判断是否触发
 * 4. 触发后返回平仓信息，由调用方生成市价平仓订单
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
@RequiredArgsConstructor
public class SlTpMonitorServiceImpl implements SlTpMonitorService {

    private final PositionService positionService;

    @Override
    public TriggerResult checkAndTrigger(long userId, String symbol, BigDecimal lastPrice) {
        Position pos = positionService.getOrCreatePosition(userId, symbol);

        // 无持仓，无需检查
        if (pos.getQuantity().signum() == 0) {
            return null;
        }

        boolean isLong = pos.getQuantity().signum() > 0;
        BigDecimal stopLoss = pos.getStopLossPrice();
        BigDecimal takeProfit = pos.getTakeProfitPrice();

        // 检查止损触发
        if (stopLoss != null && stopLoss.signum() > 0) {
            boolean slTriggered = isLong ? lastPrice.compareTo(stopLoss) <= 0 : lastPrice.compareTo(stopLoss) >= 0;
            if (slTriggered) {
                log.warn("止损触发 userId={} symbol={} position={} lastPrice={} stopLoss={}",
                        userId, symbol, pos.getQuantity(), lastPrice, stopLoss);
                // 清除 SL/TP 价格，防止重复触发
                pos.setStopLossPrice(null);
                pos.setTakeProfitPrice(null);

                // 返回平仓信息：多头平仓用 SELL，空头平仓用 BUY
                OrderSide closeSide = isLong ? OrderSide.SELL : OrderSide.BUY;
                return new TriggerResult(userId, symbol, pos.getQuantity().abs(), closeSide, "STOP_LOSS");
            }
        }

        // 检查止盈触发
        if (takeProfit != null && takeProfit.signum() > 0) {
            boolean tpTriggered = isLong ? lastPrice.compareTo(takeProfit) >= 0 : lastPrice.compareTo(takeProfit) <= 0;
            if (tpTriggered) {
                log.warn("止盈触发 userId={} symbol={} position={} lastPrice={} takeProfit={}",
                        userId, symbol, pos.getQuantity(), lastPrice, takeProfit);
                // 清除 SL/TP 价格，防止重复触发
                pos.setStopLossPrice(null);
                pos.setTakeProfitPrice(null);

                // 返回平仓信息
                OrderSide closeSide = isLong ? OrderSide.SELL : OrderSide.BUY;
                return new TriggerResult(userId, symbol, pos.getQuantity().abs(), closeSide, "TAKE_PROFIT");
            }
        }

        return null;
    }
}
