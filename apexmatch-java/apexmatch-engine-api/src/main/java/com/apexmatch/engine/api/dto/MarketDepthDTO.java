package com.apexmatch.engine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 盘口深度快照 DTO。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDepthDTO {

    private String symbol;

    @Builder.Default
    private List<DepthLevelDTO> bids = new ArrayList<>();

    @Builder.Default
    private List<DepthLevelDTO> asks = new ArrayList<>();

    /**
     * 快照生成时间（毫秒）
     */
    private long generatedTime;
}
