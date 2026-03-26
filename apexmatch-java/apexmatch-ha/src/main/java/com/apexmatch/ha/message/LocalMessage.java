package com.apexmatch.ha.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地消息表条目（最终一致性事件驱动）。
 * 业务操作与消息写入在同一本地事务中完成，定时任务扫描投递。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalMessage {

    public enum Status {
        PENDING, SENT, CONFIRMED, FAILED
    }

    private Long messageId;
    private String topic;
    private String payload;
    private Status status;
    private int retryCount;
    private long createdTime;
    private long updatedTime;
}
