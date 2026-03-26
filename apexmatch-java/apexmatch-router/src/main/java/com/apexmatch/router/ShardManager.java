package com.apexmatch.router;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分片管理器：基于一致性哈希将交易对路由到引擎节点。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class ShardManager {

    private final ConsistentHashRing<EngineNode> hashRing;

    /** 交易对 → 节点的固定映射缓存（避免迁移中交易对飘动） */
    private final Map<String, EngineNode> symbolMapping = new ConcurrentHashMap<>();

    public ShardManager(int virtualNodes) {
        this.hashRing = new ConsistentHashRing<>(virtualNodes);
    }

    /** 注册引擎节点 */
    public void registerNode(EngineNode node) {
        hashRing.addNode(node);
    }

    /** 注销引擎节点（触发关联交易对重新路由） */
    public void unregisterNode(EngineNode node) {
        hashRing.removeNode(node);
        symbolMapping.entrySet().removeIf(e -> e.getValue().equals(node));
        log.info("节点注销 nodeId={}, 关联交易对已清除", node.getNodeId());
    }

    /** 获取交易对应路由到的节点 */
    public EngineNode route(String symbol) {
        return symbolMapping.computeIfAbsent(symbol, s -> {
            EngineNode node = hashRing.getNode(s);
            if (node != null) {
                log.info("交易对路由 symbol={} → nodeId={}", s, node.getNodeId());
            }
            return node;
        });
    }

    /** 强制刷新指定交易对的路由 */
    public EngineNode reroute(String symbol) {
        symbolMapping.remove(symbol);
        return route(symbol);
    }

    /** 获取某节点负责的所有交易对 */
    public List<String> getSymbolsOnNode(EngineNode node) {
        List<String> symbols = new ArrayList<>();
        for (Map.Entry<String, EngineNode> entry : symbolMapping.entrySet()) {
            if (entry.getValue().equals(node)) {
                symbols.add(entry.getKey());
            }
        }
        return symbols;
    }

    /** 获取全部路由映射 */
    public Map<String, EngineNode> allMappings() {
        return Collections.unmodifiableMap(symbolMapping);
    }

    public int nodeCount() {
        return hashRing.size();
    }
}
