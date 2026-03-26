package com.apexmatch.risk.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.common.entity.Order;
import com.apexmatch.common.enums.OrderType;
import com.apexmatch.common.exception.ApexMatchException;
import com.apexmatch.common.exception.ErrorCode;
import com.apexmatch.risk.service.RiskControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 事前风控实现。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
@RequiredArgsConstructor
public class RiskControlServiceImpl implements RiskControlService {

    private static final String DEFAULT_CURRENCY = "USDT";

    private final AccountService accountService;

    @Override
    public void preTradeCheck(Order order, int leverage) {
        BigDecimal required = calculateRequiredMargin(order, leverage);
        BigDecimal available = accountService.getAvailableBalance(order.getUserId(), DEFAULT_CURRENCY);

        if (available.compareTo(required) < 0) {
            log.warn("保证金不足 userId={} required={} available={}",
                    order.getUserId(), required, available);
            throw new ApexMatchException(ErrorCode.INSUFFICIENT_MARGIN);
        }
        log.debug("风控通过 userId={} required={} available={}", order.getUserId(), required, available);
    }

    @Override
    public BigDecimal calculateRequiredMargin(Order order, int leverage) {
        if (order.getType() == OrderType.MARKET) {
            return order.getQuantity()
                    .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
        }
        BigDecimal price = order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO;
        return price.multiply(order.getQuantity())
                .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
    }
}
