package com.apexmatch.gateway.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void closedByDefault() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    void opensAfterThresholdFailures() {
        CircuitBreaker cb = new CircuitBreaker(3, 5000);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }

    @Test
    void halfOpenAfterCooldown() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(80);
        assertThat(cb.allowRequest()).isTrue();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void halfOpenRecoversToClosed() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.recordFailure();
        Thread.sleep(80);
        cb.allowRequest();

        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void halfOpenFailureReturnsToOpen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.recordFailure();
        Thread.sleep(80);
        cb.allowRequest();

        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void successResetFailureCount() {
        CircuitBreaker cb = new CircuitBreaker(3, 5000);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
