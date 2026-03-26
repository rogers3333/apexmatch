package com.apexmatch.settlement.service;

import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.common.entity.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * 实时清算服务：每笔成交后结算资金与持仓。
 *
 * @author luka
 * @since 2025-03-26
 */
public interface ClearingService {

    /**
     * 对一笔成交执行清算，更新买卖双方账户余额并返回资金流水。
     *
     * @param trade    成交记录
     * @param leverage 杠杆倍数
     * @return 生成的资金流水
     */
    List<FundLedgerEntry> clearTrade(Trade trade, int leverage);

    /** 计算手续费 = turnover * feeRate */
    BigDecimal calculateFee(BigDecimal turnover, boolean isMaker);
}
