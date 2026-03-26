package com.apexmatch.ha.raft;

/**
 * Raft 节点角色。
 *
 * @author luka
 * @since 2025-03-26
 */
public enum RaftNodeState {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
