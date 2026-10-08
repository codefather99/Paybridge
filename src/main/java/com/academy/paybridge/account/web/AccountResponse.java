package com.academy.paybridge.account.web;

import com.academy.paybridge.account.api.AccountView;
import java.util.UUID;

public record AccountResponse(UUID id,
                              String accountNumber,
                              UUID customerId,
                              String currency,
                              long balanceMinor,
                              String balance,
                              String status) {

    static AccountResponse from(AccountView view){
        return new AccountResponse(
                view.id(),
                view.accountNumber(),
                view.customerId(),
                view.currency().name(),
                view.balance().minorUnits(),
                view.balance().toMajor().toPlainString(),
                view.status()
        );
    }
}
