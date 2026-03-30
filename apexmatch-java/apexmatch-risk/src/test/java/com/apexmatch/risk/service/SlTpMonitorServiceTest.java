package com.apexmatch.risk.service;

import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.entity.Position;
import com.apexmatch.common.enums.OrderSide;
import com.apexmatch.risk.service.impl.SlTpMonitorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class SlTpMonitorServiceTest {

    private SlTpMonitorService slTpMonitorService;
    private PositionServiceImpl positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionServiceImpl();
        slTpMonitorService = new SlTpMonitorServiceImpl(positionService);
    }

    /** 无持仓时 checkAndTrigger 返回 null */
    @Test
    void noPositionDoesNothing() {
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("50000"));
        assertThat(result).isNull();
    }

    /** 多仓：价格跌破止损价时触发止损，清除 SL/TP */
    @Test
    void longPositionStopLossTriggeredOnPriceBelow() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setStopLossPrice(new BigDecimal("48000"));
        pos.setTakeProfitPrice(new BigDecimal("55000"));

        // 价格跌破止损价
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("47999"));

        // 应返回平仓信息
        assertThat(result).isNotNull();
        assertThat(result.getTriggerType()).isEqualTo("STOP_LOSS");
        assertThat(result.getCloseSide()).isEqualTo(OrderSide.SELL);
        assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("1"));

        // SL/TP 应被清除（触发一个后另一个自动撤销）
        assertThat(pos.getStopLossPrice()).isNull();
        assertThat(pos.getTakeProfitPrice()).isNull();
    }

    /** 多仓：价格未跌破止损价时不触发 */
    @Test
    void longPositionStopLossNotTriggeredWhenPriceAbove() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setStopLossPrice(new BigDecimal("48000"));

        // 价格高于止损价，不触发
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("49000"));

        assertThat(result).isNull();
        assertThat(pos.getStopLossPrice()).isEqualByComparingTo(new BigDecimal("48000"));
    }

    /** 多仓：价格涨破止盈价时触发止盈，清除 SL/TP */
    @Test
    void longPositionTakeProfitTriggeredOnPriceAbove() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setStopLossPrice(new BigDecimal("48000"));
        pos.setTakeProfitPrice(new BigDecimal("55000"));

        // 价格涨破止盈价
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("55001"));

        assertThat(result).isNotNull();
        assertThat(result.getTriggerType()).isEqualTo("TAKE_PROFIT");
        assertThat(result.getCloseSide()).isEqualTo(OrderSide.SELL);
        assertThat(pos.getStopLossPrice()).isNull();
        assertThat(pos.getTakeProfitPrice()).isNull();
    }

    /** 空仓：价格涨破止损价时触发止损 */
    @Test
    void shortPositionStopLossTriggeredOnPriceAbove() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.SELL,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setStopLossPrice(new BigDecimal("53000"));

        // 价格涨破止损价
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("53001"));

        assertThat(result).isNotNull();
        assertThat(result.getTriggerType()).isEqualTo("STOP_LOSS");
        assertThat(result.getCloseSide()).isEqualTo(OrderSide.BUY);
        assertThat(pos.getStopLossPrice()).isNull();
    }

    /** 空仓：价格跌破止盈价时触发止盈 */
    @Test
    void shortPositionTakeProfitTriggeredOnPriceBelow() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.SELL,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setTakeProfitPrice(new BigDecimal("46000"));

        // 价格跌破止盈价
        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("45999"));

        assertThat(result).isNotNull();
        assertThat(result.getTriggerType()).isEqualTo("TAKE_PROFIT");
        assertThat(result.getCloseSide()).isEqualTo(OrderSide.BUY);
        assertThat(pos.getTakeProfitPrice()).isNull();
    }

    /** 价格恰好等于止损价时触发 */
    @Test
    void stopLossTriggeredOnExactPrice() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        Position pos = positionService.getOrCreatePosition(1L, "BTC-USDT");
        pos.setStopLossPrice(new BigDecimal("48000"));

        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("48000"));

        assertThat(result).isNotNull();
        assertThat(result.getTriggerType()).isEqualTo("STOP_LOSS");
        assertThat(pos.getStopLossPrice()).isNull();
    }

    /** 未设置 SL/TP 时不触发 */
    @Test
    void noSlTpSetDoesNothing() {
        positionService.updateOnTrade(1L, "BTC-USDT", OrderSide.BUY,
                new BigDecimal("1"), new BigDecimal("50000"), 10);

        SlTpMonitorService.TriggerResult result = slTpMonitorService.checkAndTrigger(1L, "BTC-USDT", new BigDecimal("45000"));
        assertThat(result).isNull();
    }
}
