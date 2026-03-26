package com.apexmatch.ha.tcc;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC 事务协调器：编排 Try → Confirm / Cancel 两阶段。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class TccCoordinator {

    private final Map<String, TccStatus> txnStatusMap = new ConcurrentHashMap<>();

    /**
     * 执行一个 TCC 事务。
     *
     * @return true 表示 Confirm 成功，false 表示 Cancel 已执行
     */
    public <C> boolean execute(TccAction<C> action, C context) {
        String txnId = UUID.randomUUID().toString();
        txnStatusMap.put(txnId, TccStatus.TRYING);

        boolean tryOk;
        try {
            tryOk = action.tryAction(context);
        } catch (Exception e) {
            log.error("TCC Try 阶段异常 txnId={}: {}", txnId, e.getMessage());
            tryOk = false;
        }

        if (tryOk) {
            txnStatusMap.put(txnId, TccStatus.CONFIRMING);
            try {
                boolean confirmOk = action.confirmAction(context);
                txnStatusMap.put(txnId, confirmOk ? TccStatus.CONFIRMED : TccStatus.FAILED);
                if (!confirmOk) {
                    log.warn("TCC Confirm 失败，执行 Cancel txnId={}", txnId);
                    action.cancelAction(context);
                    txnStatusMap.put(txnId, TccStatus.CANCELLED);
                }
                return confirmOk;
            } catch (Exception e) {
                log.error("TCC Confirm 阶段异常 txnId={}: {}", txnId, e.getMessage());
                action.cancelAction(context);
                txnStatusMap.put(txnId, TccStatus.CANCELLED);
                return false;
            }
        } else {
            txnStatusMap.put(txnId, TccStatus.CANCELLING);
            try {
                action.cancelAction(context);
            } catch (Exception e) {
                log.error("TCC Cancel 阶段异常 txnId={}: {}", txnId, e.getMessage());
            }
            txnStatusMap.put(txnId, TccStatus.CANCELLED);
            return false;
        }
    }

    public TccStatus getStatus(String txnId) {
        return txnStatusMap.get(txnId);
    }
}
