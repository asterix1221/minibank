package com.minibank.account.controller;

import com.minibank.account.dto.AccountSummaryResponse;
import com.minibank.account.dto.DepositRequest;
import com.minibank.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public List<AccountSummaryResponse> myAccounts() {
        return accountService.myAccounts();
    }

    @PostMapping("/{accountId}/deposit")
    public AccountSummaryResponse deposit(@PathVariable UUID accountId, @Valid @RequestBody DepositRequest request) {
        return accountService.deposit(accountId, request.amount());
    }
}
