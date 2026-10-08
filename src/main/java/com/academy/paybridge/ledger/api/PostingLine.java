package com.academy.paybridge.ledger.api;

import com.academy.paybridge.shared.money.Money;

import java.util.Objects;
import java.util.UUID;

/** One side of a posting: this account, this direction, this amount */
public record PostingLine(UUID accountId, EntryDirection direction, Money amount) {

    public PostingLine {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(amount, "amount is required");
    }

    public static PostingLine debit(UUID accountId, Money amount) {
        return new PostingLine(accountId, EntryDirection.DEBIT, amount);
    }

    public static PostingLine credit(UUID accountId, Money amount){
        return new PostingLine(accountId, EntryDirection.CREDIT, amount);
    }
}
