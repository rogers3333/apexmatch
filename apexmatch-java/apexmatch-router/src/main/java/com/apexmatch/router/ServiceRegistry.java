package com.apexmatch.router;

import java.util.List;
import java.util.function.Consumer;

/**
 * 服务发现抽象接口。
 * 生产环境由 etcd / Consul / Nacos 实现。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface ServiceRegistry {

    /** 注册节点 */
    void register(EngineNode node);

    /** 注销节点 */
    void deregister(EngineNode node);

    /** 获取所有存活节点 */
    List<EngineNode> getAliveNodes();

    /** 监听节点变化 */
    void watch(Consumer<List<EngineNode>> callback);
}
