package com.apexmatch.gateway.filter;

import lombok.extern.slf4j.Slf4j;

/**
 * 令牌桶限流器。
 * <p>
 * 按固定速率填充令牌，每次请求消耗一个令牌。
 * 桶满时多余令牌丢弃，桶空时请求被拒绝。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class TokenBucketRateLimiter {

    private final long maxTokens;
    private final double refillRatePerMs;
    private double tokens;
    private long lastRefillTimeMs;

    /**
     * @param maxTokens       桶容量
     * @param refillPerSecond 每秒填充速率
     */
    public TokenBucketRateLimiter(long maxTokens, double refillPerSecond) {
        this.maxTokens = maxTokens;
        this.refillRatePerMs = refillPerSecond / 1000.0;
        this.tokens = maxTokens;
        this.lastRefillTimeMs = System.currentTimeMillis();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized double availableTokens() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTimeMs;
        if (elapsed > 0) {
            tokens = Math.min(maxTokens, tokens + elapsed * refillRatePerMs);
            lastRefillTimeMs = now;
        }
    }
}
