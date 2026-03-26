package com.apexmatch.common.entity;

import com.apexmatch.common.enums.MarginMode;
import com.apexmatch.common.enums.PositionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 合约/杠杆持仓快照（支持单向与双向）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    private Long userId;

    private String symbol;

    /**
     * 单向模式下净持仓数量（多正空负）；双向模式下可与多空字段组合使用
     */
    private BigDecimal quantity;

    /**
     * 双向模式：多仓数量
     */
    private BigDecimal longQuantity;

    /**
     * 双向模式：空仓数量
     */
    private BigDecimal shortQuantity;

    /**
     * 多仓开仓均价
     */
    private BigDecimal longEntryPrice;

    /**
     * 空仓开仓均价
     */
    private BigDecimal shortEntryPrice;

    private MarginMode marginMode;

    private PositionMode positionMode;

    /**
     * 已占用保证金
     */
    private BigDecimal isolatedMargin;

    /**
     * 未实现盈亏（实时行情计算）
     */
    private BigDecimal unrealizedPnl;

    /**
     * 杠杆倍数
     */
    private Integer leverage;
}
