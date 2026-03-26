package com.apexmatch.common.constant;

/**
 * Redis 键前缀约定，便于集群与隔离。
 *
 * @author luka
 * @since 2025-03-26
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    public static final String PREFIX = "apex:";

    public static String accountBalance(Long userId, String currency) {
        return PREFIX + "acct:bal:" + userId + ":" + currency;
    }

    public static String orderBookShard(String symbol) {
        return PREFIX + "ob:shard:" + symbol;
    }
}
