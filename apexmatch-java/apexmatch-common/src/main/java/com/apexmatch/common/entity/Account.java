package com.apexmatch.common.entity;

import com.apexmatch.common.enums.MarginMode;
import com.apexmatch.common.enums.PositionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户交易账户。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    private Long accountId;

    private Long userId;

    /** 币种，如 USDT */
    private String currency;

    /** 可用余额 */
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /** 冻结余额（已委托占用） */
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Builder.Default
    private MarginMode marginMode = MarginMode.CROSS;

    @Builder.Default
    private PositionMode positionMode = PositionMode.ONE_WAY;

    private long createdTime;

    private long updatedTime;

    /** 总权益 = 可用 + 冻结 */
    public BigDecimal totalEquity() {
        return balance.add(frozenBalance);
    }
}
