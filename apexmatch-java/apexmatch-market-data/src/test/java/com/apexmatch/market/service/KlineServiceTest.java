package com.apexmatch.market.service;

import com.apexmatch.common.entity.Kline;
import com.apexmatch.common.entity.Trade;
import com.apexmatch.market.service.impl.KlineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KlineServiceTest {

    private KlineService klineService;

    @BeforeEach
    void setUp() {
        klineService = new KlineServiceImpl();
    }

    @Test
    void singleTradeCreatesKline() {
        Trade trade = Trade.builder()
                .tradeId(1L)
                .symbol("BTC-USDT")
                .price(new BigDecimal("50000"))
                .quantity(new BigDecimal("1"))
                .takerOrderId(1L).makerOrderId(2L)
                .takerUserId(1L).makerUserId(2L)
                .tradeTime(System.currentTimeMillis())
                .build();

        klineService.onTrade(trade);
        Kline kline = klineService.getLatest("BTC-USDT", "1m");

        assertThat(kline).isNotNull();
        assertThat(kline.getOpen()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(kline.getClose()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(kline.getHigh()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(kline.getLow()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(kline.getVolume()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(kline.getTradeCount()).isEqualTo(1);
    }

    @Test
    void multipleTradesSameMinuteMerge() {
        long now = System.currentTimeMillis();
        long minuteBase = now / 60_000L * 60_000L;

        klineService.onTrade(makeTrade(1L, "50000", "1", minuteBase + 1000));
        klineService.onTrade(makeTrade(2L, "51000", "2", minuteBase + 2000));
        klineService.onTrade(makeTrade(3L, "49000", "0.5", minuteBase + 3000));

        Kline kline = klineService.getLatest("BTC-USDT", "1m");
        assertThat(kline.getOpen()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(kline.getHigh()).isEqualByComparingTo(new BigDecimal("51000"));
        assertThat(kline.getLow()).isEqualByComparingTo(new BigDecimal("49000"));
        assertThat(kline.getClose()).isEqualByComparingTo(new BigDecimal("49000"));
        assertThat(kline.getVolume()).isEqualByComparingTo(new BigDecimal("3.5"));
        assertThat(kline.getTradeCount()).isEqualTo(3);
    }

    @Test
    void differentMinutesCreateSeparateKlines() {
        long now = System.currentTimeMillis();
        long minuteBase = now / 60_000L * 60_000L;

        klineService.onTrade(makeTrade(1L, "50000", "1", minuteBase + 1000));
        klineService.onTrade(makeTrade(2L, "51000", "2", minuteBase + 60_000 + 1000));

        List<Kline> klines = klineService.getKlines("BTC-USDT", "1m", 10);
        assertThat(klines).hasSize(2);
    }

    @Test
    void multipleIntervalsUpdated() {
        klineService.onTrade(makeTrade(1L, "50000", "1", System.currentTimeMillis()));

        assertThat(klineService.getLatest("BTC-USDT", "1m")).isNotNull();
        assertThat(klineService.getLatest("BTC-USDT", "5m")).isNotNull();
        assertThat(klineService.getLatest("BTC-USDT", "1h")).isNotNull();
        assertThat(klineService.getLatest("BTC-USDT", "1d")).isNotNull();
    }

    private Trade makeTrade(long id, String price, String qty, long time) {
        return Trade.builder()
                .tradeId(id)
                .symbol("BTC-USDT")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .takerOrderId(id).makerOrderId(id + 100)
                .takerUserId(1L).makerUserId(2L)
                .tradeTime(time)
                .build();
    }

    /** 无成交时 getTicker24h 返回全零统计 */
    @Test
    void ticker24hEmptyWhenNoTrades() {
        KlineService.Ticker24h ticker = klineService.getTicker24h("BTC-USDT");
        assertThat(ticker).isNotNull();
        assertThat(ticker.symbol()).isEqualTo("BTC-USDT");
        assertThat(ticker.lastPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ticker.volume()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** 有成交时 getTicker24h 返回正确统计 */
    @Test
    void ticker24hAggregatesCorrectly() {
        long now = System.currentTimeMillis();
        // 模拟最近 3 分钟的成交
        klineService.onTrade(makeTrade(1L, "50000", "1", now - 120_000));
        klineService.onTrade(makeTrade(2L, "52000", "2", now - 60_000));
        klineService.onTrade(makeTrade(3L, "48000", "0.5", now - 30_000));

        KlineService.Ticker24h ticker = klineService.getTicker24h("BTC-USDT");
        assertThat(ticker.highPrice()).isEqualByComparingTo(new BigDecimal("52000"));
        assertThat(ticker.lowPrice()).isEqualByComparingTo(new BigDecimal("48000"));
        // 最新价 = 最后一笔成交价
        assertThat(ticker.lastPrice()).isEqualByComparingTo(new BigDecimal("48000"));
        // 成交量 = 1 + 2 + 0.5 = 3.5
        assertThat(ticker.volume()).isEqualByComparingTo(new BigDecimal("3.5"));
    }

    /** 涨跌幅计算正确 */
    @Test
    void ticker24hPriceChangePercent() {
        long now = System.currentTimeMillis();
        klineService.onTrade(makeTrade(1L, "50000", "1", now - 120_000));
        klineService.onTrade(makeTrade(2L, "55000", "1", now - 30_000));

        KlineService.Ticker24h ticker = klineService.getTicker24h("BTC-USDT");
        // priceChange = 55000 - 50000 = 5000
        assertThat(ticker.priceChange()).isEqualByComparingTo(new BigDecimal("5000"));
        // priceChangePercent = 5000/50000 * 100 = 10%
        assertThat(ticker.priceChangePercent()).isEqualByComparingTo(new BigDecimal("10.00000000"));
    }
}
