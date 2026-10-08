package com.academy.paybridge.transfer.domain;

import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransferType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "source_account_number", nullable = false, updatable = false, length = 10)
    private String sourceAccountNumber;

    @Column(name = "destination_account_id", updatable = false)
    private UUID destinationAccountId;

    @Column(name = "destination_account_number", nullable = false, updatable = false, length = 10)
    private String destinationAccountNumber;

    @Column(name = "destination_bank_code", updatable = false, length = 10)
    private String destinationBankCode;

    @Column(name = "destination_account_name", updatable = false, length = 150)
    private String destinationAccountName;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Column(updatable = false, length = 100)
    private String narration;

    @Column(name = "ledger_transaction_id")
    private UUID ledgerTransactionId;

    @Column(name = "reversal_ledger_transaction_id")
    private UUID reversalLedgerTransactionId;

    @Column(name = "provider_recipient_code", updatable = false, length = 50)
    private String providerRecipientCode;

    @Column(name = "provider_reference", length = 60)
    private String providerReference;

    @Column(name = "provider_transfer_code", length = 50)
    private String providerTransferCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {
    }

    public static Transfer initiateInternal(String idempotencyKey,
                                            UUID sourceAccountId, String sourceAccountNumber,
                                            UUID destinationAccountId, String destinationAccountNumber,
                                            Money amount, String narration, Instant now) {
        Transfer t = new Transfer();
        t.idempotencyKey = idempotencyKey;
        t.type = TransferType.INTERNAL;
        t.status = TransferStatus.PENDING;
        t.sourceAccountId = sourceAccountId;
        t.sourceAccountNumber = sourceAccountNumber;
        t.destinationAccountId = destinationAccountId;
        t.destinationAccountNumber = destinationAccountNumber;
        t.amountMinor = amount.minorUnits();
        t.currency = amount.currency();
        t.narration = narration;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public static Transfer initiateExternal(String idempotencyKey,
                                            UUID sourceAccountId, String sourceAccountNumber,
                                            String destinationAccountNumber, String destinationBankCode,
                                            String destinationAccountName, String providerRecipientCode,
                                            Money amount, String narration, Instant now) {
        Transfer t = new Transfer();
        t.idempotencyKey = idempotencyKey;
        t.type = TransferType.EXTERNAL;
        t.status = TransferStatus.PENDING;
        t.sourceAccountId = sourceAccountId;
        t.sourceAccountNumber = sourceAccountNumber;
        t.destinationAccountNumber = destinationAccountNumber;
        t.destinationBankCode = destinationBankCode;
        t.destinationAccountName = destinationAccountName;
        t.providerRecipientCode = providerRecipientCode;
        t.amountMinor = amount.minorUnits();
        t.currency = amount.currency();
        t.narration = narration;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    /** Internal transfers: money moved in one step. */
    public void complete(UUID ledgerTransactionId, Instant now) {
        moveTo(TransferStatus.COMPLETED, now);
        this.ledgerTransactionId = ledgerTransactionId;
    }

    /** External transfers: funds are held in the clearing account, waiting on the provider. */
    public void markProcessing(UUID ledgerTransactionId, Instant now) {
        moveTo(TransferStatus.PROCESSING, now);
        this.ledgerTransactionId = ledgerTransactionId;
    }

    public void markCompleted(Instant now) {
        moveTo(TransferStatus.COMPLETED, now);
    }

    /** The money was returned to the source account by a reversal posting. */
    public void reverse(String reason, UUID reversalLedgerTransactionId, Instant now) {
        moveTo(TransferStatus.REVERSED, now);
        this.failureReason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
        this.reversalLedgerTransactionId = reversalLedgerTransactionId;
    }

    public void fail(String reason, Instant now) {
        moveTo(TransferStatus.FAILED, now);
        this.failureReason = reason;
    }

    public void assignProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public void recordProviderTransferCode(String providerTransferCode) {
        this.providerTransferCode = providerTransferCode;
    }

    private void moveTo(TransferStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "Transfer cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransferType getType() { return type; }
    public TransferStatus getStatus() { return status; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public String getDestinationBankCode() { return destinationBankCode; }
    public String getDestinationAccountName() { return destinationAccountName; }
    public Money getAmount() { return new Money(amountMinor, currency); }
    public String getNarration() { return narration; }
    public UUID getLedgerTransactionId() { return ledgerTransactionId; }
    public UUID getReversalLedgerTransactionId() { return reversalLedgerTransactionId; }
    public String getProviderRecipientCode() { return providerRecipientCode; }
    public String getProviderReference() { return providerReference; }
    public String getProviderTransferCode() { return providerTransferCode; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}