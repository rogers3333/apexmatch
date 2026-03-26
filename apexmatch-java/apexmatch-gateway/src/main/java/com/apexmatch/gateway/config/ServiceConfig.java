package com.apexmatch.gateway.config;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.account.service.PositionService;
import com.apexmatch.account.service.impl.AccountServiceImpl;
import com.apexmatch.account.service.impl.PositionServiceImpl;
import com.apexmatch.common.util.SnowflakeIdGenerator;
import com.apexmatch.engine.api.MatchingEngine;
import com.apexmatch.engine.java.JavaMatchingEngine;
import com.apexmatch.gateway.disruptor.OrderDisruptorService;
import com.apexmatch.market.service.KlineService;
import com.apexmatch.market.service.impl.KlineServiceImpl;
import com.apexmatch.settlement.service.ClearingService;
import com.apexmatch.settlement.service.impl.ClearingServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务服务 Bean 注册（内存实现，生产环境替换为持久化实现）。
 *
 * @author luka
 * @since 2025-03-26
 */
@Configuration
public class ServiceConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(1, 1);
    }

    @Bean
    public MatchingEngine matchingEngine() {
        JavaMatchingEngine engine = new JavaMatchingEngine();
        engine.init("BTC-USDT", null);
        engine.init("ETH-USDT", null);
        return engine;
    }

    @Bean
    public AccountService accountService() {
        return new AccountServiceImpl();
    }

    @Bean
    public PositionService positionService() {
        return new PositionServiceImpl();
    }

    @Bean
    public ClearingService clearingService(AccountService accountService, PositionService positionService) {
        return new ClearingServiceImpl(accountService, positionService);
    }

    @Bean
    public KlineService klineService() {
        return new KlineServiceImpl();
    }

    @Bean
    public OrderDisruptorService orderDisruptorService(MatchingEngine matchingEngine) {
        return new OrderDisruptorService(matchingEngine);
    }
}
