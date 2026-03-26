package com.apexmatch.ha.tcc;

import com.apexmatch.common.entity.Order;
import com.apexmatch.engine.api.dto.MatchResultDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单 TCC 事务上下文。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
public class OrderPlacementContext {
    private final Order order;
    private final long userId;
    private final String currency;
    private final int leverage;

    /** Try 阶段冻结的金额 */
    private BigDecimal frozenAmount;

    /** Confirm 阶段的撮合结果 */
    private MatchResultDTO result;
}
