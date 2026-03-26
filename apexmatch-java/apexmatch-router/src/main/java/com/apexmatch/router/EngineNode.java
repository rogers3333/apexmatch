package com.apexmatch.router;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撮合引擎节点信息。
 *
 * @author luka
 * @since 2025-03-26
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineNode {

    private String nodeId;
    private String host;
    private int port;
    private boolean alive;

    /** 该节点负责的交易对数量 */
    private int symbolCount;

    public String address() {
        return host + ":" + port;
    }
}
