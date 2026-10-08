package com.academy.paybridge.ledger.domain;

import com.academy.paybridge.ledger.api.EntryDirection;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 6)
    private EntryDirection direction;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(name = "balance_after_minor", nullable = false, updatable = false)
    private long balanceAfterMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry of(UUID transactionId, UUID accountId, EntryDirection direction,
                                 Money amount, Money balanceAfter, Instant now) {
        LedgerEntry entry = new LedgerEntry();
        entry.transactionId = transactionId;
        entry.accountId = accountId;
        entry.direction = direction;
        entry.amountMinor = amount.minorUnits();
        entry.currency = amount.currency();
        entry.balanceAfterMinor = balanceAfter.minorUnits();
        entry.createdAt = now;
        return entry;
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public EntryDirection getDirection() { return direction; }
    public Money getAmount() { return new Money(amountMinor, currency); }
    public Money getBalanceAfter() { return new Money(balanceAfterMinor, currency); }
    public Instant getCreatedAt() { return createdAt; }
}