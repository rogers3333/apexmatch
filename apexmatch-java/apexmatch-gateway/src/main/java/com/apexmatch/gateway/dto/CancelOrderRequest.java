package com.apexmatch.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤单请求 DTO。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "撤单请求")
public class CancelOrderRequest {

    @NotNull
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @NotBlank
    @Schema(description = "交易对", example = "BTC-USDT")
    private String symbol;

    @NotNull
    @Schema(description = "订单ID", example = "123456789")
    private Long orderId;
}
