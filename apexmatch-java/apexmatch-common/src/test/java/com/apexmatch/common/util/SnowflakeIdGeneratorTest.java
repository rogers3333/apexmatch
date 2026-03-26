package com.apexmatch.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SnowflakeIdGenerator} 单元测试。
 *
 * @author luka
 * @since 2025-03-26
 */
class SnowflakeIdGeneratorTest {

    @Test
    void nextId_monotonicAndUnique() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1, 1);
        long previous = -1L;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            long id = gen.nextId();
            assertThat(id).isGreaterThan(previous);
            assertThat(seen.add(id)).isTrue();
            previous = id;
        }
    }

    @Test
    void constructor_rejectsInvalidWorker() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
