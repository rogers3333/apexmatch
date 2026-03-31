package com.apexmatch.dex.service;

import com.apexmatch.dex.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DexAggregatorService {

    private final Map<Long, DexProtocol> protocols = new ConcurrentHashMap<>();
    private final Map<Long, LiquidityPool> pools = new ConcurrentHashMap<>();
    private final List<DexSwapRecord> swapRecords = new ArrayList<>();

    public DexProtocol addProtocol(String protocolName, String chainCode, String routerAddress,
                                   BigDecimal feeRate) {
        DexProtocol protocol = new DexProtocol();
        protocol.setProtocolId(System.currentTimeMillis());
        protocol.setProtocolName(protocolName);
        protocol.setChainCode(chainCode);
        protocol.setRouterAddress(routerAddress);
        protocol.setFeeRate(feeRate);
        protocol.setIsActive(true);
        protocol.setCreatedAt(LocalDateTime.now());
        protocols.put(protocol.getProtocolId(), protocol);
        log.info("添加 DEX 协议: protocolId={}, name={}, chain={}", protocol.getProtocolId(), protocolName, chainCode);
        return protocol;
    }

    public LiquidityPool addPool(Long protocolId, String chainCode, String poolAddress,
                                 String token0, String token1, BigDecimal reserve0, BigDecimal reserve1) {
        LiquidityPool pool = new LiquidityPool();
        pool.setPoolId(System.currentTimeMillis());
        pool.setProtocolId(protocolId);
        pool.setChainCode(chainCode);
        pool.setPoolAddress(poolAddress);
        pool.setToken0(token0);
        pool.setToken1(token1);
        pool.setReserve0(reserve0);
        pool.setReserve1(reserve1);
        pool.setLiquidity(reserve0.multiply(reserve1).sqrt(new java.math.MathContext(8)));
        pools.put(pool.getPoolId(), pool);
        log.info("添加流动性池: poolId={}, token0={}, token1={}", pool.getPoolId(), token0, token1);
        return pool;
    }

    public SwapRoute findBestRoute(String fromToken, String toToken, BigDecimal amountIn) {
        SwapRoute bestRoute = null;
        BigDecimal bestAmountOut = BigDecimal.ZERO;

        for (LiquidityPool pool : pools.values()) {
            if (pool.getToken0().equals(fromToken) && pool.getToken1().equals(toToken)) {
                BigDecimal amountOut = calculateAmountOut(amountIn, pool.getReserve0(), pool.getReserve1());
                if (amountOut.compareTo(bestAmountOut) > 0) {
                    bestAmountOut = amountOut;
                    bestRoute = buildRoute(pool, fromToken, toToken, amountIn, amountOut);
                }
            } else if (pool.getToken1().equals(fromToken) && pool.getToken0().equals(toToken)) {
                BigDecimal amountOut = calculateAmountOut(amountIn, pool.getReserve1(), pool.getReserve0());
                if (amountOut.compareTo(bestAmountOut) > 0) {
                    bestAmountOut = amountOut;
                    bestRoute = buildRoute(pool, fromToken, toToken, amountIn, amountOut);
                }
            }
        }

        log.info("找到最佳路由: from={}, to={}, amountIn={}, amountOut={}",
                 fromToken, toToken, amountIn, bestAmountOut);
        return bestRoute;
    }

    private BigDecimal calculateAmountOut(BigDecimal amountIn, BigDecimal reserveIn, BigDecimal reserveOut) {
        BigDecimal amountInWithFee = amountIn.multiply(new BigDecimal("997"));
        BigDecimal numerator = amountInWithFee.multiply(reserveOut);
        BigDecimal denominator = reserveIn.multiply(new BigDecimal("1000")).add(amountInWithFee);
        return numerator.divide(denominator, 8, RoundingMode.DOWN);
    }

    private SwapRoute buildRoute(LiquidityPool pool, String fromToken, String toToken,
                                 BigDecimal amountIn, BigDecimal amountOut) {
        SwapRoute route = new SwapRoute();
        route.setFromToken(fromToken);
        route.setToToken(toToken);
        route.setAmountIn(amountIn);
        route.setAmountOut(amountOut);
        route.setPath(Arrays.asList(fromToken, toToken));
        route.setProtocolIds(Collections.singletonList(pool.getProtocolId()));
        route.setPriceImpact(BigDecimal.ZERO);
        route.setTotalFee(amountIn.multiply(new BigDecimal("0.003")));
        return route;
    }

    public DexSwapRecord executeSwap(Long userId, String chainCode, SwapRoute route) {
        DexSwapRecord record = new DexSwapRecord();
        record.setSwapId(System.currentTimeMillis());
        record.setUserId(userId);
        record.setChainCode(chainCode);
        record.setProtocolId(route.getProtocolIds().get(0));
        record.setFromToken(route.getFromToken());
        record.setToToken(route.getToToken());
        record.setAmountIn(route.getAmountIn());
        record.setAmountOut(route.getAmountOut());
        record.setTxHash("0x" + UUID.randomUUID().toString().replace("-", ""));
        record.setStatus("COMPLETED");
        record.setExecutedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        swapRecords.add(record);
        log.info("执行 DEX 兑换: swapId={}, userId={}, from={}, to={}, amountOut={}",
                 record.getSwapId(), userId, route.getFromToken(), route.getToToken(), route.getAmountOut());
        return record;
    }

    public List<DexProtocol> getActiveProtocols() {
        return protocols.values().stream()
                .filter(DexProtocol::getIsActive)
                .toList();
    }

    public List<DexSwapRecord> getUserSwaps(Long userId) {
        return swapRecords.stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList();
    }
}
