package com.apexmatch.gateway.controller;

import com.apexmatch.account.service.AccountService;
import com.apexmatch.common.entity.Account;
import com.apexmatch.common.entity.FundLedgerEntry;
import com.apexmatch.gateway.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户 REST API。
 *
 * @author luka
 * @since 2025-03-26
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Tag(name = "账户管理", description = "查询余额 / 充值 / 提现")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{userId}")
    @Operation(summary = "查询账户", description = "获取账户余额与冻结资金")
    public ApiResponse<Account> getAccount(@PathVariable long userId,
                                           @RequestParam(defaultValue = "USDT") String currency) {
        try {
            return ApiResponse.success(accountService.getAccount(userId, currency));
        } catch (Exception e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/{userId}/deposit")
    @Operation(summary = "充值", description = "向账户充值指定金额")
    public ApiResponse<String> deposit(@PathVariable long userId,
                                       @RequestParam(defaultValue = "USDT") String currency,
                                       @RequestParam BigDecimal amount) {
        accountService.createAccount(userId, currency);
        accountService.deposit(userId, currency, amount);
        return ApiResponse.success("充值成功");
    }

    @PostMapping("/{userId}/withdraw")
    @Operation(summary = "提现", description = "从账户提现指定金额")
    public ApiResponse<String> withdraw(@PathVariable long userId,
                                        @RequestParam(defaultValue = "USDT") String currency,
                                        @RequestParam BigDecimal amount) {
        try {
            accountService.withdraw(userId, currency, amount);
            return ApiResponse.success("提现成功");
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/{userId}/ledger")
    @Operation(summary = "资金流水", description = "查询账户资金变动记录")
    public ApiResponse<List<FundLedgerEntry>> getLedger(@PathVariable long userId,
                                                        @RequestParam(defaultValue = "USDT") String currency) {
        return ApiResponse.success(accountService.getLedger(userId, currency));
    }
}
