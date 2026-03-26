package com.apexmatch.ha.tcc;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 下单 TCC 实现：
 * <ul>
 *   <li>Try：冻结保证金</li>
 *   <li>Confirm：提交订单到撮合引擎</li>
 *   <li>Cancel：解冻保证金</li>
 * </ul>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class OrderPlacementTcc implements TccAction<OrderPlacementContext> {

    private final AccountService accountService;
    private final MatchingEngine engine;

    @Override
    public boolean tryAction(OrderPlacementContext ctx) {
        BigDecimal required = calculateMargin(ctx.getOrder(), ctx.getLeverage());
        try {
            accountService.freezeMargin(ctx.getUserId(), ctx.getCurrency(), required);
            ctx.setFrozenAmount(required);
            log.info("TCC-Try 冻结保证金 userId={} amount={}", ctx.getUserId(), required);
            return true;
        } catch (Exception e) {
            log.warn("TCC-Try 保证金不足 userId={}: {}", ctx.getUserId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean confirmAction(OrderPlacementContext ctx) {
        MatchResultDTO result = engine.submitOrder(ctx.getOrder());
        ctx.setResult(result);
        log.info("TCC-Confirm 提交订单 orderId={} trades={}",
                ctx.getOrder().getOrderId(), result.getTrades().size());
        return true;
    }

    @Override
    public boolean cancelAction(OrderPlacementContext ctx) {
        if (ctx.getFrozenAmount() != null && ctx.getFrozenAmount().signum() > 0) {
            accountService.unfreezeMargin(ctx.getUserId(), ctx.getCurrency(), ctx.getFrozenAmount());
            log.info("TCC-Cancel 解冻保证金 userId={} amount={}", ctx.getUserId(), ctx.getFrozenAmount());
        }
        return true;
    }

    private BigDecimal calculateMargin(Order order, int leverage) {
        BigDecimal price = order.getPrice() != null ? order.getPrice() : BigDecimal.ONE;
        return price.multiply(order.getQuantity())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
    }
}
