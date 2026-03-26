package com.apexmatch.gateway.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void initialBucketFull() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 100);
        assertThat(limiter.availableTokens()).isEqualTo(10.0);
    }

    @Test
    void acquireConsumesToken() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 100);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.availableTokens()).isLessThan(10.0);
    }

    @Test
    void bucketEmptyRejectsRequest() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 0);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void refillRestoresTokens() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1000);
        for (int i = 0; i < 5; i++) limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse();

        Thread.sleep(50);
        assertThat(limiter.availableTokens()).isGreaterThan(0);
    }
}
