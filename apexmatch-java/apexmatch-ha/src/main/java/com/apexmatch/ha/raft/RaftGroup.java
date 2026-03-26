package com.apexmatch.ha.raft;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Raft 组模拟：管理一组状态机副本，实现 Leader 选举与日志复制。
 * <p>
 * 生产环境替换为 SOFAJRaft 的 {@code RaftGroupService}。
 * 本实现用于单元测试与开发阶段验证状态机一致性。
 * </p>
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class RaftGroup {

    @Getter
    private final String groupId;

    private final List<MatchingEngineStateMachine> replicas;

    @Getter
    private int leaderIndex = 0;

    private final AtomicLong nextLogIndex = new AtomicLong(1);
    private final AtomicLong currentTerm = new AtomicLong(1);

    public RaftGroup(String groupId, List<MatchingEngineStateMachine> replicas) {
        this.groupId = groupId;
        this.replicas = new CopyOnWriteArrayList<>(replicas);
        if (!replicas.isEmpty()) {
            log.info("Raft 组 [{}] 初始化，节点数={}，Leader=node-0", groupId, replicas.size());
        }
    }

    /** 获取当前 Leader 状态机 */
    public MatchingEngineStateMachine leader() {
        return replicas.get(leaderIndex);
    }

    /**
     * 提交操作日志：Leader 创建日志 → 复制到多数节点 → 应用。
     *
     * @return Leader 状态机的执行结果
     */
    public Object propose(OperationLog.OpType type,
                          com.apexmatch.common.entity.Order order,
                          String symbol, Long orderId) {
        long index = nextLogIndex.getAndIncrement();
        long term = currentTerm.get();

        OperationLog entry = OperationLog.builder()
                .logIndex(index)
                .term(term)
                .type(type)
                .order(order)
                .symbol(symbol)
                .orderId(orderId)
                .timestamp(System.currentTimeMillis())
                .build();

        int majority = replicas.size() / 2 + 1;
        int acked = 0;
        Object leaderResult = null;

        for (int i = 0; i < replicas.size(); i++) {
            try {
                Object result = replicas.get(i).onApply(entry);
                acked++;
                if (i == leaderIndex) {
                    leaderResult = result;
                }
            } catch (Exception e) {
                log.warn("节点 {} 应用日志失败: {}", i, e.getMessage());
            }
        }

        if (acked < majority) {
            log.error("日志未达多数确认 acked={} majority={}", acked, majority);
        }

        return leaderResult;
    }

    /**
     * 模拟 Leader 切换（主节点宕机后重新选举）。
     */
    public void triggerLeaderElection(int newLeaderIndex) {
        if (newLeaderIndex < 0 || newLeaderIndex >= replicas.size()) {
            throw new IllegalArgumentException("无效节点索引: " + newLeaderIndex);
        }
        int oldLeader = leaderIndex;
        currentTerm.incrementAndGet();
        leaderIndex = newLeaderIndex;
        log.info("Leader 切换: node-{} → node-{}, term={}", oldLeader, newLeaderIndex, currentTerm.get());
    }

    public int replicaCount() {
        return replicas.size();
    }

    public MatchingEngineStateMachine getReplica(int index) {
        return replicas.get(index);
    }
}
