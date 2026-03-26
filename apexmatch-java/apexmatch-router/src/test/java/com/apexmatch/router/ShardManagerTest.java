package com.apexmatch.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ShardManagerTest {

    private ShardManager shardManager;
    private EngineNode node1, node2, node3;

    @BeforeEach
    void setUp() {
        shardManager = new ShardManager(100);
        node1 = EngineNode.builder().nodeId("node-1").host("10.0.0.1").port(8080).alive(true).build();
        node2 = EngineNode.builder().nodeId("node-2").host("10.0.0.2").port(8080).alive(true).build();
        node3 = EngineNode.builder().nodeId("node-3").host("10.0.0.3").port(8080).alive(true).build();
        shardManager.registerNode(node1);
        shardManager.registerNode(node2);
        shardManager.registerNode(node3);
    }

    @Test
    void routeDeterministic() {
        EngineNode first = shardManager.route("BTC-USDT");
        EngineNode second = shardManager.route("BTC-USDT");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSymbolsMayRouteDifferently() {
        shardManager.route("BTC-USDT");
        shardManager.route("ETH-USDT");
        shardManager.route("SOL-USDT");

        assertThat(shardManager.allMappings()).hasSize(3);
    }

    @Test
    void nodeUnregisterClearsMappings() {
        shardManager.route("BTC-USDT");
        EngineNode btcNode = shardManager.route("BTC-USDT");

        shardManager.unregisterNode(btcNode);
        assertThat(shardManager.nodeCount()).isEqualTo(2);

        EngineNode newNode = shardManager.reroute("BTC-USDT");
        assertThat(newNode).isNotEqualTo(btcNode);
    }

    @Test
    void getSymbolsOnNode() {
        EngineNode btcNode = shardManager.route("BTC-USDT");
        List<String> symbols = shardManager.getSymbolsOnNode(btcNode);
        assertThat(symbols).contains("BTC-USDT");
    }

    @Test
    void serviceRegistryIntegration() {
        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.register(node1);
        registry.register(node2);

        assertThat(registry.getAliveNodes()).hasSize(2);

        registry.deregister(node1);
        assertThat(registry.getAliveNodes()).hasSize(1);
    }
}
