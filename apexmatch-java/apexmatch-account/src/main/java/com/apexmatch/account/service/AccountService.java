package com.apexmatch.account.service;

import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.FundLedgerEntry;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户资金管理服务。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface AccountService {

    Account createAccount(long userId, String currency);

    Account getAccount(long userId, String currency);

    void deposit(long userId, String currency, BigDecimal amount);

    void withdraw(long userId, String currency, BigDecimal amount);

    /** 委托冻结保证金 */
    void freezeMargin(long userId, String currency, BigDecimal amount);

    /** 撤单/成交后解冻保证金 */
    void unfreezeMargin(long userId, String currency, BigDecimal amount);

    /** 扣减余额（如手续费扣除、亏损结算） */
    void debit(long userId, String currency, BigDecimal amount, Long refOrderId, Long refTradeId);

    /** 增加余额（如盈利结算） */
    void credit(long userId, String currency, BigDecimal amount, Long refOrderId, Long refTradeId);

    BigDecimal getAvailableBalance(long userId, String currency);

    List<FundLedgerEntry> getLedger(long userId, String currency);
}
