package com.apexmatch.router;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 基于内存的服务注册中心（开发/测试用）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class InMemoryServiceRegistry implements ServiceRegistry {

    private final List<EngineNode> nodes = new CopyOnWriteArrayList<>();
    private final List<Consumer<List<EngineNode>>> watchers = new CopyOnWriteArrayList<>();

    @Override
    public void register(EngineNode node) {
        node.setAlive(true);
        nodes.add(node);
        notifyWatchers();
        log.info("节点注册 nodeId={} address={}", node.getNodeId(), node.address());
    }

    @Override
    public void deregister(EngineNode node) {
        nodes.removeIf(n -> n.getNodeId().equals(node.getNodeId()));
        notifyWatchers();
        log.info("节点注销 nodeId={}", node.getNodeId());
    }

    @Override
    public List<EngineNode> getAliveNodes() {
        List<EngineNode> alive = new ArrayList<>();
        for (EngineNode n : nodes) {
            if (n.isAlive()) alive.add(n);
        }
        return alive;
    }

    @Override
    public void watch(Consumer<List<EngineNode>> callback) {
        watchers.add(callback);
    }

    private void notifyWatchers() {
        List<EngineNode> alive = getAliveNodes();
        for (Consumer<List<EngineNode>> w : watchers) {
            w.accept(alive);
        }
    }
}
