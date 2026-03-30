package com.apexmatch.risk.service.impl;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.common.entity.Position;
import com.apexmatch.risk.service.AdlService;
import com.apexmatch.risk.service.InsuranceFundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ADL 自动减仓服务实现。
 * 优先级计算：PnL% × leverage，盈利越多、杠杆越高的持仓优先被减仓。
 *
 * @author luka
 * @since 2025-03-30
 */
@Slf4j
@RequiredArgsConstructor
public class AdlServiceImpl implements AdlService {

    private static final BigDecimal ADL_THRESHOLD = new BigDecimal("1000");
    private static final int SCALE = 8;

    private final PositionService positionService;
    private final AccountService accountService;
    private final InsuranceFundService insuranceFundService;

    @Override
    public BigDecimal executeAdl(String symbol, BigDecimal targetLoss,
                                   BigDecimal markPrice, String currency) {
        log.warn("触发 ADL symbol={} targetLoss={} markPrice={}", symbol, targetLoss, markPrice);

        // TODO: 实际实现需要从持仓管理器获取所有该交易对的持仓
        // 这里使用简化实现，假设有一个全局持仓列表
        List<AdlCandidate> candidates = buildAdlQueue(symbol, markPrice);

        BigDecimal coveredLoss = BigDecimal.ZERO;
        for (AdlCandidate candidate : candidates) {
            if (coveredLoss.compareTo(targetLoss) >= 0) {
                break;
            }

            BigDecimal pnl = candidate.pnl;
            if (pnl.signum() <= 0) {
                continue;
            }

            BigDecimal toReduce = targetLoss.subtract(coveredLoss).min(pnl);
            reducePosition(candidate.userId, symbol, candidate.position, toReduce, markPrice, currency);
            coveredLoss = coveredLoss.add(toReduce);

            log.info("ADL 减仓 userId={} symbol={} reducedPnl={} priority={}",
                    candidate.userId, symbol, toReduce, candidate.priority);
        }

        log.info("ADL 执行完成 symbol={} targetLoss={} coveredLoss={}", symbol, targetLoss, coveredLoss);
        return coveredLoss;
    }

    @Override
    public boolean shouldTriggerAdl(String currency) {
        BigDecimal balance = insuranceFundService.getBalance(currency);
        return balance.compareTo(ADL_THRESHOLD) < 0;
    }

    private List<AdlCandidate> buildAdlQueue(String symbol, BigDecimal markPrice) {
        // TODO: 实际实现需要从全局持仓管理器获取所有持仓
        // 这里返回空列表作为占位符
        return Collections.emptyList();
    }

    private void reducePosition(long userId, String symbol, Position pos,
                                 BigDecimal reducePnl, BigDecimal markPrice, String currency) {
        BigDecimal pnl = positionService.calculateUnrealizedPnl(pos, markPrice);
        if (pnl.signum() <= 0) {
            return;
        }

        BigDecimal reduceRatio = reducePnl.divide(pnl, SCALE, RoundingMode.HALF_UP);
        BigDecimal reduceQty = pos.getQuantity().abs().multiply(reduceRatio);

        accountService.credit(userId, currency, reducePnl, null, null);

        if (reduceRatio.compareTo(BigDecimal.ONE) >= 0) {
            pos.setQuantity(BigDecimal.ZERO);
            pos.setLongQuantity(BigDecimal.ZERO);
            pos.setShortQuantity(BigDecimal.ZERO);
        } else {
            if (pos.getQuantity().signum() > 0) {
                pos.setQuantity(pos.getQuantity().subtract(reduceQty));
            } else {
                pos.setQuantity(pos.getQuantity().add(reduceQty));
            }
        }
    }

    private static class AdlCandidate {
        long userId;
        Position position;
        BigDecimal pnl;
        BigDecimal priority;

        AdlCandidate(long userId, Position position, BigDecimal pnl, BigDecimal priority) {
            this.userId = userId;
            this.position = position;
            this.pnl = pnl;
            this.priority = priority;
        }
    }
}
