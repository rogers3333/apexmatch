package com.apexmatch.account.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.common.enums.FundChangeType;
import com.apexmatch.common.exception.ApexMatchException;
import com.apexmatch.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 账户服务内存实现。
 * 生产环境由 MyBatis-Plus + Redis 替代存储层。
 *
 * @author luka
 * @since 2025-03-26
 */
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<FundLedgerEntry>> ledgers = new ConcurrentHashMap<>();
    private final AtomicLong ledgerIdGen = new AtomicLong(1);

    private String key(long userId, String currency) {
        return userId + ":" + currency;
    }

    @Override
    public Account createAccount(long userId, String currency) {
        String k = key(userId, currency);
        Account account = Account.builder()
                .accountId(userId)
                .userId(userId)
                .currency(currency)
                .createdTime(System.currentTimeMillis())
                .updatedTime(System.currentTimeMillis())
                .build();
        accounts.putIfAbsent(k, account);
        ledgers.putIfAbsent(k, new CopyOnWriteArrayList<>());
        return accounts.get(k);
    }

    @Override
    public Account getAccount(long userId, String currency) {
        Account account = accounts.get(key(userId, currency));
        if (account == null) {
            throw new ApexMatchException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    @Override
    public synchronized void deposit(long userId, String currency, BigDecimal amount) {
        Account account = getOrCreate(userId, currency);
        account.setBalance(account.getBalance().add(amount));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.TRANSFER_IN, amount,
                account.getBalance(), null, null);
        log.info("存入 userId={} amount={} balance={}", userId, amount, account.getBalance());
    }

    @Override
    public synchronized void withdraw(long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId, currency);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ApexMatchException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        account.setBalance(account.getBalance().subtract(amount));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.TRANSFER_OUT, amount.negate(),
                account.getBalance(), null, null);
    }

    @Override
    public synchronized void freezeMargin(long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId, currency);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ApexMatchException(ErrorCode.INSUFFICIENT_MARGIN);
        }
        account.setBalance(account.getBalance().subtract(amount));
        account.setFrozenBalance(account.getFrozenBalance().add(amount));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.FREEZE, amount.negate(),
                account.getBalance(), null, null);
    }

    @Override
    public synchronized void unfreezeMargin(long userId, String currency, BigDecimal amount) {
        Account account = getAccount(userId, currency);
        BigDecimal actual = amount.min(account.getFrozenBalance());
        account.setFrozenBalance(account.getFrozenBalance().subtract(actual));
        account.setBalance(account.getBalance().add(actual));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.UNFREEZE, actual,
                account.getBalance(), null, null);
    }

    @Override
    public synchronized void debit(long userId, String currency, BigDecimal amount,
                                   Long refOrderId, Long refTradeId) {
        Account account = getAccount(userId, currency);
        account.setBalance(account.getBalance().subtract(amount));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.DEBIT, amount.negate(),
                account.getBalance(), refOrderId, refTradeId);
    }

    @Override
    public synchronized void credit(long userId, String currency, BigDecimal amount,
                                    Long refOrderId, Long refTradeId) {
        Account account = getAccount(userId, currency);
        account.setBalance(account.getBalance().add(amount));
        account.setUpdatedTime(System.currentTimeMillis());
        appendLedger(userId, currency, FundChangeType.CREDIT, amount,
                account.getBalance(), refOrderId, refTradeId);
    }

    @Override
    public BigDecimal getAvailableBalance(long userId, String currency) {
        return getAccount(userId, currency).getBalance();
    }

    @Override
    public List<FundLedgerEntry> getLedger(long userId, String currency) {
        CopyOnWriteArrayList<FundLedgerEntry> list = ledgers.get(key(userId, currency));
        return list != null ? new ArrayList<>(list) : List.of();
    }

    private Account getOrCreate(long userId, String currency) {
        String k = key(userId, currency);
        if (!accounts.containsKey(k)) {
            createAccount(userId, currency);
        }
        return accounts.get(k);
    }

    private void appendLedger(long userId, String currency, FundChangeType type,
                              BigDecimal amount, BigDecimal balanceAfter,
                              Long refOrderId, Long refTradeId) {
        FundLedgerEntry entry = FundLedgerEntry.builder()
                .ledgerId(ledgerIdGen.getAndIncrement())
                .userId(userId)
                .accountType("FUTURES")
                .currency(currency)
                .changeType(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .refOrderId(refOrderId)
                .refTradeId(refTradeId)
                .createdTime(System.currentTimeMillis())
                .build();
        ledgers.computeIfAbsent(key(userId, currency), k -> new CopyOnWriteArrayList<>())
                .add(entry);
    }
}
