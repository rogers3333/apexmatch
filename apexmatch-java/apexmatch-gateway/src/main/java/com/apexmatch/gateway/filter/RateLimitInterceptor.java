package com.apexmatch.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.apexmatch.gateway.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流 + 熔断拦截器。
 *
 * @author luka
 * @since 2025-03-26
 */
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final TokenBucketRateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!circuitBreaker.allowRequest()) {
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "服务熔断中，请稍后重试");
            return false;
        }

        if (!rateLimiter.tryAcquire()) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "请求过于频繁，请稍后重试");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(status, message));
    }
}
