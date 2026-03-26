package com.apexmatch.risk.service;

import com.apexmatch.common.entity.Order;

import java.math.BigDecimal;

/**
 * 事前风控服务：下单前校验。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface RiskControlService {

    /**
     * 下单前风控校验：余额是否充足、仓位限制等。
     *
     * @param order    待提交的订单
     * @param leverage 杠杆倍数
     * @throws com.apexmatch.common.exception.ApexMatchException 校验不通过时抛出
     */
    void preTradeCheck(Order order, int leverage);

    /** 计算下单所需保证金 = price × quantity / leverage */
    BigDecimal calculateRequiredMargin(Order order, int leverage);
}
