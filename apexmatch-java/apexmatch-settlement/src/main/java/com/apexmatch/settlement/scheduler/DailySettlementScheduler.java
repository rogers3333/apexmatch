package com.apexmatch.settlement.scheduler;

import com.apexmatch.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 日终结算定时任务。
 * <p>
 * 每日 00:00 UTC 触发日终结算，包括：
 * 1. 资金费率结算（每 8 小时一次）
 * 2. 日终对账
 * </p>
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySettlementScheduler {

    private final SettlementService settlementService;

    /**
     * 资金费率结算：每 8 小时执行一次（00:00, 08:00, 16:00 UTC）。
     */
    @Scheduled(cron = "0 0 0,8,16 * * *", zone = "UTC")
    public void fundingRateSettlement() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        log.info("开始资金费率结算 time={}", now);

        // 资金费率（实际生产中从 FundingRateService 获取）
        BigDecimal fundingRate = new BigDecimal("0.0001");

        // TODO: 遍历所有持仓用户，执行资金费率结算
        // positionService.getAllOpenPositions().forEach(pos -> {
        //     BigDecimal positionValue = pos.getQuantity().multiply(pos.getEntryPrice());
        //     BigDecimal fee = settlementService.calculateFundingFee(positionValue, fundingRate);
        //     settlementService.applyFundingFee(pos.getUserId(), "USDT", fee);
        // });

        log.info("资金费率结算完成 fundingRate={}", fundingRate);
    }

    /**
     * 日终对账：每日 00:05 UTC 执行（错开整点，避免与资金费率结算冲突）。
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void dailyReconciliation() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        log.info("开始日终对账 date={}", now.toLocalDate());

        // TODO: 遍历所有活跃账户执行对账
        // accountService.getAllActiveUserIds().forEach(userId -> {
        //     boolean ok = settlementService.reconcile(userId, "USDT");
        //     if (!ok) {
        //         log.error("对账异常 userId={}", userId);
        //         alertService.sendAlert("日终对账异常 userId=" + userId);
        //     }
        // });

        log.info("日终对账完成 date={}", now.toLocalDate());
    }
}
