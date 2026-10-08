package com.academy.paybridge.account.web;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.customer.api.CurrentCustomer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountApi accountApi;

    public AccountController(AccountApi accountApi) {
        this.accountApi = accountApi;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new account for the authenticated customer")
    public AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(accountApi.openAccount(CurrentCustomer.id(), request.currency()));
    }

    @GetMapping
    @Operation(summary = "List my accounts")
    public List<AccountResponse> mine() {
        return accountApi.listForCustomer(CurrentCustomer.id()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get one of my accounts")
    public AccountResponse get(@PathVariable String accountNumber) {
        return AccountResponse.from(accountApi.requireOwned(accountNumber, CurrentCustomer.id()));
    }
}