package com.apexmatch.gateway.config;

import com.apexmatch.gateway.filter.CircuitBreaker;
import com.apexmatch.gateway.filter.RateLimitInterceptor;
import com.apexmatch.gateway.filter.TokenBucketRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册限流 + 熔断拦截器。
 *
 * @author luka
 * @since 2025-03-26
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public TokenBucketRateLimiter tokenBucketRateLimiter() {
        return new TokenBucketRateLimiter(1000, 500);
    }

    @Bean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker(10, 30_000);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(tokenBucketRateLimiter(), circuitBreaker()))
                .addPathPatterns("/api/**");
    }
}
