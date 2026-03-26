package com.apexmatch.ha.tcc;

/**
 * TCC 分布式事务参与者接口。
 *
 * @param <C> 事务上下文类型
 * @author luka
 * @since 2025-03-26
 */
public interface TccAction<C> {

    /** Try 阶段：资源预留（如冻结保证金） */
    boolean tryAction(C context);

    /** Confirm 阶段：执行业务（如提交订单） */
    boolean confirmAction(C context);

    /** Cancel 阶段：回滚资源（如解冻保证金） */
    boolean cancelAction(C context);
}
