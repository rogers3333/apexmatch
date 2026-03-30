package com.apexmatch.ha.raft;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SOFAJRaft 状态机适配器。
 * <p>
 * 将 {@link MatchingEngineStateMachine} 适配到 SOFAJRaft 的 {@link com.alipay.sofa.jraft.StateMachine} 接口。
 * </p>
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
public class SofaRaftStateMachine extends StateMachineAdapter {

    private final MatchingEngineStateMachine engineStateMachine;
    private final AtomicLong appliedIndex = new AtomicLong(0);

    public SofaRaftStateMachine(MatchingEngineStateMachine engineStateMachine) {
        this.engineStateMachine = engineStateMachine;
    }

    @Override
    public void onApply(Iterator iter) {
        while (iter.hasNext()) {
            OperationLog entry;
            Closure done = iter.done();

            if (iter.getData() != null) {
                ByteBuffer data = iter.getData();
                try {
                    entry = deserialize(data);
                    entry.setLogIndex(iter.getIndex());
                    entry.setTerm(iter.getTerm());
                } catch (Exception e) {
                    log.error("反序列化日志失败 index={}", iter.getIndex(), e);
                    if (done != null) {
                        done.run(new Status(RaftError.EINTERNAL, "反序列化失败"));
                    }
                    iter.next();
                    continue;
                }

                Object result = engineStateMachine.onApply(entry);
                appliedIndex.set(iter.getIndex());

                if (done != null) {
                    done.run(Status.OK());
                }

                log.debug("应用日志 index={} type={}", iter.getIndex(), entry.getType());
            }

            iter.next();
        }
    }

    @Override
    public void onSnapshotSave(SnapshotWriter writer, Closure done) {
        long lastApplied = appliedIndex.get();
        log.info("保存快照 lastAppliedIndex={}", lastApplied);
        done.run(Status.OK());
    }

    @Override
    public boolean onSnapshotLoad(SnapshotReader reader) {
        log.info("加载快照 path={}", reader.getPath());
        return true;
    }

    @Override
    public void onLeaderStart(long term) {
        log.info("成为 Leader term={}", term);
        super.onLeaderStart(term);
    }

    @Override
    public void onLeaderStop(Status status) {
        log.info("Leader 下台 status={}", status);
        super.onLeaderStop(status);
    }

    private OperationLog deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (OperationLog) ois.readObject();
        }
    }
}
