package com.apexmatch.common.constant;

/**
 * 撮合与引擎相关常量。
 *
 * @author luka
 * @since 2025-03-26
 */
public final class EngineConstants {

    private EngineConstants() {
    }

    /**
     * 默认盘口深度档位数
     */
    public static final int DEFAULT_DEPTH_LEVELS = 50;

    /**
     * 配置键：引擎类型 java / rust
     */
    public static final String CONFIG_ENGINE_TYPE = "apexmatch.engine.type";
}
