package com.academy.paybridge.account.api;

import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;

import java.util.UUID;

/** Read only snapshot of an account, safe to hand to other modules **/
public record AccountView(
        UUID id,
        String accountNumber,
        UUID customerId,
        Currency currency,
        Money balance,
        String status,
        String accountType
) {
}
