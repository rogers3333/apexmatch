package com.apexmatch.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MoneyUtils} 单元测试。
 *
 * @author luka
 * @since 2025-03-26
 */
class MoneyUtilsTest {

    @Test
    void parseDecimal_valid() {
        assertThat(MoneyUtils.parseDecimal(" 1.25 "))
                .isEqualByComparingTo(new BigDecimal("1.25"));
    }

    @Test
    void parseDecimal_invalidOrBlank_returnsNull() {
        assertThat(MoneyUtils.parseDecimal(null)).isNull();
        assertThat(MoneyUtils.parseDecimal("")).isNull();
        assertThat(MoneyUtils.parseDecimal("abc")).isNull();
    }

    @Test
    void roundDown() {
        BigDecimal v = new BigDecimal("1.239");
        assertThat(MoneyUtils.roundDown(v, 2)).isEqualByComparingTo(new BigDecimal("1.23"));
    }
}
