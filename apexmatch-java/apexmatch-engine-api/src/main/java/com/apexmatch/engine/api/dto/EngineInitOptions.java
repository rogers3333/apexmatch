package com.apexmatch.engine.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引擎初始化可选参数。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineInitOptions {

    /**
     * WAL 目录
     */
    private String walDirectory;

    /**
     * 快照目录
     */
    private String snapshotDirectory;

    /**
     * 是否恢复自最近快照
     */
    private boolean recoverFromSnapshot;
}
