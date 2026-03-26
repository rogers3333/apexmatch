package com.apexmatch.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 下单请求 DTO。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "下单请求")
public class PlaceOrderRequest {

    @NotNull
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @NotBlank
    @Schema(description = "交易对", example = "BTC-USDT")
    private String symbol;

    @NotBlank
    @Schema(description = "买卖方向：BUY / SELL", example = "BUY")
    private String side;

    @NotBlank
    @Schema(description = "订单类型：LIMIT / MARKET", example = "LIMIT")
    private String type;

    @Schema(description = "有效期类型：GTC / IOC / FOK", example = "GTC")
    private String timeInForce;

    @Schema(description = "委托价格（限价单必填）", example = "50000.00")
    private BigDecimal price;

    @NotNull
    @DecimalMin("0.00000001")
    @Schema(description = "委托数量", example = "1.5")
    private BigDecimal quantity;

    @Schema(description = "止损/止盈触发价", example = "48000.00")
    private BigDecimal stopPrice;

    @Schema(description = "冰山单展示数量")
    private BigDecimal displayQuantity;

    @Schema(description = "杠杆倍数", example = "10")
    private Integer leverage;
}
