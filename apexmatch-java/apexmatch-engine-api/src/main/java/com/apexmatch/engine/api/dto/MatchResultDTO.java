package com.apexmatch.engine.api.dto;

import com.apexmatch.common.entity.Order;
import com.apexmatch.common.entity.Trade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 submit 的撮合输出。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultDTO {

    @Builder.Default
    private List<Trade> trades = new ArrayList<>();

    /**
     * 处理后的订单（含部分成交剩余、状态变更）
     */
    private Order affectedOrder;

    /**
     * 被拒绝时的错误码（引擎内部错误），可选
     */
    private String rejectReason;
}
