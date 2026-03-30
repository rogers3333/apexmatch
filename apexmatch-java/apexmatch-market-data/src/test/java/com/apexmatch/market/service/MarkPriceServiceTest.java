package com.apexmatch.market.service;

import com.apexmatch.market.service.impl.MarkPriceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MarkPriceServiceTest {

    private MarkPriceService markPriceService;

    @BeforeEach
    void setUp() {
        markPriceService = new MarkPriceServiceImpl();
    }

    /** 初始标记价格为 0 */
    @Test
    void initialMarkPriceIsZero() {
        assertThat(markPriceService.getMarkPrice("BTC-USDT"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** 首次更新时标记价格等于最新成交价（无盘口时） */
    @Test
    void firstUpdateSetsMarkPriceToLastTradePrice() {
        markPriceService.updateMarkPrice("BTC-USDT", new BigDecimal("50000"), null, null);
        assertThat(markPriceService.getMarkPrice("BTC-USDT"))
                .isEqualByComparingTo(new BigDecimal("50000"));
    }

    /** 有买卖盘时标记价格为成交价与中间价均值 */
    @Test
    void markPriceAveragesTradeAndMidPrice() {
        // lastPrice=50000, bid=49900, ask=50100 → mid=50000 → rawMark=50000
        markPriceService.updateMarkPrice("BTC-USDT",
                new BigDecimal("50000"),
                new BigDecimal("49900"),
                new BigDecimal("50100"));

        BigDecimal mark = markPriceService.getMarkPrice("BTC-USDT");
        assertThat(mark).isEqualByComparingTo(new BigDecimal("50000.00000000"));
    }

    /** EMA 平滑：连续更新后标记价格不直接跳变 */
    @Test
    void markPriceIsSmoothened() {
        // 初始成交价 50000
        markPriceService.updateMarkPrice("BTC-USDT", new BigDecimal("50000"), null, null);

        // 突然价格跳到 60000（单边行情）
        markPriceService.updateMarkPrice("BTC-USDT", new BigDecimal("60000"), null, null);

        BigDecimal mark = markPriceService.getMarkPrice("BTC-USDT");
        // EMA: 0.3 * 60000 + 0.7 * 50000 = 18000 + 35000 = 53000
        assertThat(mark).isEqualByComparingTo(new BigDecimal("53000.00000000"));
        // 标记价格应小于最新成交价（平滑效果）
        assertThat(mark).isLessThan(new BigDecimal("60000"));
    }

    /** 不同交易对的标记价格相互独立 */
    @Test
    void markPricesAreIndependentPerSymbol() {
        markPriceService.updateMarkPrice("BTC-USDT", new BigDecimal("50000"), null, null);
        markPriceService.updateMarkPrice("ETH-USDT", new BigDecimal("3000"), null, null);

        assertThat(markPriceService.getMarkPrice("BTC-USDT"))
                .isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(markPriceService.getMarkPrice("ETH-USDT"))
                .isEqualByComparingTo(new BigDecimal("3000"));
    }
}
