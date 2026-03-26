package com.apexmatch.router;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 一致性哈希环，支持虚拟节点。
 *
 * @param <T> 节点类型
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class ConsistentHashRing<T> {

    private final TreeMap<Long, T> ring = new TreeMap<>();
    private final Map<T, Set<Long>> nodeHashes = new HashMap<>();
    private final int virtualNodes;

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    /** 添加物理节点（含 virtualNodes 个虚拟节点） */
    public void addNode(T node) {
        Set<Long> hashes = new HashSet<>();
        for (int i = 0; i < virtualNodes; i++) {
            long hash = hash(node.toString() + "#VN" + i);
            ring.put(hash, node);
            hashes.add(hash);
        }
        nodeHashes.put(node, hashes);
        log.info("节点上线 node={} 虚拟节点数={} 环大小={}", node, virtualNodes, ring.size());
    }

    /** 移除物理节点及其全部虚拟节点 */
    public void removeNode(T node) {
        Set<Long> hashes = nodeHashes.remove(node);
        if (hashes != null) {
            hashes.forEach(ring::remove);
            log.info("节点下线 node={} 环大小={}", node, ring.size());
        }
    }

    /** 根据 key 定位物理节点（顺时针找最近虚拟节点） */
    public T getNode(String key) {
        if (ring.isEmpty()) return null;
        long hash = hash(key);
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }

    /** 获取所有物理节点 */
    public Set<T> allNodes() {
        return Collections.unmodifiableSet(nodeHashes.keySet());
    }

    public int size() {
        return nodeHashes.size();
    }

    public int ringSize() {
        return ring.size();
    }

    /**
     * 统计各物理节点负责的虚拟节点比例（用于验证均匀性）。
     */
    public Map<T, Integer> distribution() {
        Map<T, Integer> counts = new HashMap<>();
        for (T node : ring.values()) {
            counts.merge(node, 1, Integer::sum);
        }
        return counts;
    }

    static long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[0] & 0xFF))
                    | ((long) (digest[1] & 0xFF) << 8)
                    | ((long) (digest[2] & 0xFF) << 16)
                    | ((long) (digest[3] & 0xFF) << 24)
                    | ((long) (digest[4] & 0xFF) << 32)
                    | ((long) (digest[5] & 0xFF) << 40)
                    | ((long) (digest[6] & 0xFF) << 48)
                    | ((long) (digest[7] & 0xFF) << 56);
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode();
        }
    }
}
