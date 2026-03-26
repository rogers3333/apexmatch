package com.apexmatch.router;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ConsistentHashRingTest {

    @Test
    void singleNodeRoutesAll() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("node-1");

        assertThat(ring.getNode("BTC-USDT")).isEqualTo("node-1");
        assertThat(ring.getNode("ETH-USDT")).isEqualTo("node-1");
    }

    @Test
    void multipleNodesDistributeEvenly() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(200);
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Map<String, Integer> distribution = ring.distribution();
        assertThat(distribution).hasSize(3);

        // 200 虚拟节点 × 3 物理节点 = 600 个环节点
        assertThat(ring.ringSize()).isEqualTo(600);

        // 每个节点应有 ~200 个虚拟节点
        for (int count : distribution.values()) {
            assertThat(count).isEqualTo(200);
        }
    }

    @Test
    void routingDeterministic() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("node-1");
        ring.addNode("node-2");

        String first = ring.getNode("BTC-USDT");
        String second = ring.getNode("BTC-USDT");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void removeNodeReroutesKeys() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("node-1");
        ring.addNode("node-2");

        ring.removeNode("node-1");
        assertThat(ring.getNode("BTC-USDT")).isEqualTo("node-2");
        assertThat(ring.size()).isEqualTo(1);
    }

    @Test
    void emptyRingReturnsNull() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        assertThat(ring.getNode("BTC-USDT")).isNull();
    }

    @Test
    void goodDistributionAcross1000Keys() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(150);
        ring.addNode("A");
        ring.addNode("B");
        ring.addNode("C");

        int[] counts = new int[3];
        String[] nodes = {"A", "B", "C"};
        for (int i = 0; i < 1000; i++) {
            String node = ring.getNode("symbol-" + i);
            for (int j = 0; j < 3; j++) {
                if (nodes[j].equals(node)) counts[j]++;
            }
        }

        // 期望每个节点分配 ~333 个 key（±30% 偏差可接受）
        for (int c : counts) {
            assertThat(c).isBetween(200, 500);
        }
    }
}
