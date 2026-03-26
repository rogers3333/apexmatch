package com.apexmatch.gateway.filter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 熔断器：基于滑动窗口的失败计数。
 * <ul>
 *   <li>CLOSED：正常放行，累计失败次数</li>
 *   <li>OPEN：全部拒绝，超过冷却时间后进入 HALF_OPEN</li>
 *   <li>HALF_OPEN：试探性放行一个请求，成功则 CLOSED，失败则回到 OPEN</li>
 * </ul>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    @Getter
    private volatile State state = State.CLOSED;

    private final int failureThreshold;
    private final long cooldownMs;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /**
     * @param failureThreshold 触发熔断的连续失败次数
     * @param cooldownMs       熔断冷却时间（毫秒）
     */
    public CircuitBreaker(int failureThreshold, long cooldownMs) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    public boolean allowRequest() {
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime.get() >= cooldownMs) {
                    state = State.HALF_OPEN;
                    log.info("熔断器进入 HALF_OPEN 状态");
                    yield true;
                }
                yield false;
            }
            case HALF_OPEN -> true;
        };
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            log.info("熔断器恢复 CLOSED 状态");
        }
        state = State.CLOSED;
        failureCount.set(0);
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int count = failureCount.incrementAndGet();
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn("HALF_OPEN 试探失败，回到 OPEN 状态");
        } else if (count >= failureThreshold) {
            state = State.OPEN;
            log.warn("连续失败 {} 次，触发熔断", count);
        }
    }
}
