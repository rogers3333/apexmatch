package com.apexmatch.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额与数量格式化工具（避免 double 精度问题）。
 *
 * @author luka
 * @since 2025-03-26
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    /**
     * 将字符串解析为 {@link BigDecimal}，非法时返回 null。
     *
     * @param raw 原始字符串
     * @return 有效值或 null
     */
    public static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 按指定小数位向下取整（用于数量对齐，业务侧可替换策略）。
     *
     * @param value    原值
     * @param scale    小数位数
     * @return 取整后的值
     */
    public static BigDecimal roundDown(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.DOWN);
    }
}
