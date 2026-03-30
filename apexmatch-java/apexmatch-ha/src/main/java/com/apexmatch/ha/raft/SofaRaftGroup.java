package com.apexmatch.ha.raft;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.apexmatch.common.entity.Order;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于 SOFAJRaft 的真实 Raft 组实现。
 * <p>
 * 替换内存 mock 的 {@link RaftGroup}，实现真实的日志复制和 Leader 选举。
 * 每个撮合分片对应一个 Raft 组，配置 3 节点（1 主 2 从）。
 * </p>
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
public class SofaRaftGroup {

    private final String groupId;
    private final RaftGroupService raftGroupService;
    private final Node node;
    private final SofaRaftStateMachine stateMachine;

    /**
     * @param groupId    Raft 组 ID（通常为交易对名称，如 "BTC-USDT"）
     * @param localPeer  本节点地址，格式 "host:port"
     * @param peers      集群所有节点地址，逗号分隔，如 "host1:port1,host2:port2,host3:port3"
     * @param dataPath   Raft 日志和快照存储路径
     * @param stateMachine 状态机实现
     */
    public SofaRaftGroup(String groupId, String localPeer, String peers,
                         String dataPath, SofaRaftStateMachine stateMachine) {
        this.groupId = groupId;
        this.stateMachine = stateMachine;

        PeerId serverId = PeerId.parsePeer(localPeer);

        NodeOptions nodeOptions = new NodeOptions();
        nodeOptions.setFsm(stateMachine);
        nodeOptions.setLogUri(dataPath + "/log");
        nodeOptions.setRaftMetaUri(dataPath + "/meta");
        nodeOptions.setSnapshotUri(dataPath + "/snapshot");
        nodeOptions.setElectionTimeoutMs(5000);
        nodeOptions.setSnapshotIntervalSecs(30);

        Configuration initConf = new Configuration();
        for (String peer : peers.split(",")) {
            initConf.addPeer(PeerId.parsePeer(peer.trim()));
        }
        nodeOptions.setInitialConf(initConf);

        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions);
        this.node = raftGroupService.start();

        log.info("SOFAJRaft 组 [{}] 启动，本节点={}, 集群={}", groupId, localPeer, peers);
    }

    /**
     * 提交下单操作到 Raft 日志。
     */
    public CompletableFuture<Object> proposeSubmitOrder(Order order) {
        OperationLog entry = OperationLog.submit(0, 0, order);
        return propose(entry);
    }

    /**
     * 提交撤单操作到 Raft 日志。
     */
    public CompletableFuture<Object> proposeCancelOrder(String symbol, long orderId) {
        OperationLog entry = OperationLog.cancel(0, 0, symbol, orderId);
        return propose(entry);
    }

    private CompletableFuture<Object> propose(OperationLog entry) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        if (!node.isLeader()) {
            future.completeExceptionally(
                new IllegalStateException("当前节点不是 Leader，请重定向到 Leader: " + node.getLeaderId()));
            return future;
        }

        ByteBuffer data;
        try {
            data = serialize(entry);
        } catch (IOException e) {
            future.completeExceptionally(e);
            return future;
        }

        Task task = new Task();
        task.setData(data);
        task.setDone(status -> {
            if (status.isOk()) {
                future.complete(null);
            } else {
                future.completeExceptionally(new RuntimeException("Raft 提交失败: " + status.getErrorMsg()));
            }
        });

        node.apply(task);
        return future;
    }

    /** 是否为当前 Leader */
    public boolean isLeader() {
        return node.isLeader();
    }

    /** 获取当前 Leader 节点 ID */
    public String getLeaderId() {
        PeerId leader = node.getLeaderId();
        return leader != null ? leader.toString() : null;
    }

    /** 关闭 Raft 组 */
    public void shutdown() {
        raftGroupService.shutdown();
        try {
            raftGroupService.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SOFAJRaft 组 [{}] 已关闭", groupId);
    }

    private ByteBuffer serialize(OperationLog entry) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(entry);
        }
        return ByteBuffer.wrap(bos.toByteArray());
    }
}
