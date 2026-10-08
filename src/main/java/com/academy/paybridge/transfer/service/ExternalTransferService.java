package com.academy.paybridge.transfer.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountView;
import com.academy.paybridge.account.api.SystemAccounts;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.ledger.api.PostingResult;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.api.ExternalTransferApi;
import com.academy.paybridge.transfer.api.ExternalTransferCommand;
import com.academy.paybridge.transfer.api.TransferResult;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.client.GatewayRejectedException;
import com.academy.paybridge.transfer.client.GatewayUnavailableException;
import com.academy.paybridge.transfer.client.TransferGateway;
import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferRequest;
import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferResult;
import com.academy.paybridge.transfer.client.TransferGateway.ResolvedAccount;
import com.academy.paybridge.transfer.domain.Transfer;
import com.academy.paybridge.transfer.domain.TransferStatus;
import com.academy.paybridge.transfer.domain.TransferType;
import com.academy.paybridge.transfer.repository.TransferRepository;
import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExternalTransferService implements ExternalTransferApi {

    private static final Logger log = LoggerFactory.getLogger(ExternalTransferService.class);

    private final TransferRepository transferRepository;
    private final AccountApi accountApi;
    private final LedgerApi ledgerApi;
    private final TransferGateway gateway;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration providerGrace;

    public ExternalTransferService(TransferRepository transferRepository,
                                   AccountApi accountApi,
                                   LedgerApi ledgerApi,
                                   TransferGateway gateway,
                                   TransactionTemplate transactionTemplate,
                                   Clock clock,
                                   @Value("${paybridge.transfers.provider-grace:2m}") Duration providerGrace) {
        this.transferRepository = transferRepository;
        this.accountApi = accountApi;
        this.ledgerApi = ledgerApi;
        this.gateway = gateway;
        this.tx = transactionTemplate;
        this.clock = clock;
        this.providerGrace = providerGrace;
    }

    // NOTE: this method is deliberately NOT @Transactional. It manages its own short transactions.
    @Override
    public TransferResult transferExternal(ExternalTransferCommand command) {
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        // ---- Phase 1: pre-flight. No money has moved, no locks are held. ----
        AccountView source = accountApi.getByAccountNumber(command.sourceAccountNumber());
        validateSource(source);
        Money amount = Money.ofMajor(command.amount(), source.currency());

        ResolvedAccount recipient = gateway.resolveAccount(
                command.destinationAccountNumber(), command.destinationBankCode());
        String recipientCode = gateway.createRecipient(
                recipient.accountName(), command.destinationAccountNumber(), command.destinationBankCode());

        // ---- Phase 2: hold the customer's funds. One short transaction. ----
        Transfer transfer = tx.execute(status ->
                holdFunds(command, source, recipient.accountName(), recipientCode, amount));

        // ---- Phase 3: talk to the provider. NO transaction is open. ----
        Outcome outcome;
        try {
            GatewayTransferResult result = gateway.initiateTransfer(new GatewayTransferRequest(
                    transfer.getProviderReference(), recipientCode, amount, reason(command)));
            outcome = outcomeOf(result);
        } catch (GatewayRejectedException e) {
            outcome = Outcome.reverse("Provider rejected the transfer: " + e.getMessage(), null);
        } catch (GatewayUnavailableException e) {
            // The request may or may not have reached the provider. We do NOT know. Leave it PROCESSING.
            log.warn("Outcome unknown for transfer {}; leaving it PROCESSING until the provider is asked",
                    transfer.getId());
            return new TransferResult(TransferService.toView(transfer), false);
        }

        // ---- Phase 4: apply the outcome. Another short transaction. ----
        Transfer settled = settle(transfer.getId(), outcome);
        return new TransferResult(TransferService.toView(settled), false);
    }

    @Override
    public TransferView refresh(UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSFER_NOT_FOUND", "No transfer with id " + transferId));

        if (transfer.getType() != TransferType.EXTERNAL || transfer.getStatus() != TransferStatus.PROCESSING) {
            return TransferService.toView(transfer);   // nothing to resolve
        }

        Optional<GatewayTransferResult> remote = gateway.verifyTransfer(transfer.getProviderReference());

        Outcome outcome;
        if (remote.isPresent()) {
            outcome = outcomeOf(remote.get());
        } else {
            // The provider has no record. That only proves the request never arrived once enough
            // time has passed for it to have shown up if it had.
            Duration age = Duration.between(transfer.getUpdatedAt(), clock.instant());
            if (age.compareTo(providerGrace) < 0) {
                return TransferService.toView(transfer);
            }
            outcome = Outcome.reverse("Provider has no record of this transfer", null);
        }
        return TransferService.toView(settle(transferId, outcome));
    }

    @Override
    public Optional<TransferView> applyProviderUpdate(String providerReference, GatewayTransferStatus status,
                                                      String providerCode, Long reportedAmountMinor) {
        Optional<Transfer> found = transferRepository.findByProviderReference(providerReference);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Transfer transfer = found.get();

        if (reportedAmountMinor != null && reportedAmountMinor.longValue() != transfer.getAmount().minorUnits()) {
            throw new BusinessException("AMOUNT_MISMATCH",
                    "Provider reported " + reportedAmountMinor + " kobo but transfer "
                            + transfer.getId() + " is for " + transfer.getAmount().minorUnits());
        }

        Outcome outcome = outcomeOf(new GatewayTransferResult(
                providerReference, providerCode, status, "provider update"));
        return Optional.of(TransferService.toView(settle(transfer.getId(), outcome)));
    }

    // ---------------------------------------------------------------------------------------

    private Transfer holdFunds(ExternalTransferCommand command, AccountView source,
                               String accountName, String recipientCode, Money amount) {
        Instant now = clock.instant();

        // Flush so a concurrent duplicate Idempotency-Key fails here, before any money moves.
        Transfer transfer = transferRepository.saveAndFlush(Transfer.initiateExternal(
                command.idempotencyKey(),
                source.id(), source.accountNumber(),
                command.destinationAccountNumber(), command.destinationBankCode(),
                accountName, recipientCode,
                amount, command.narration(), now));

        // Lowercase letters, digits and '-' only: the format Paystack accepts for references.
        transfer.assignProviderReference("trf_" + transfer.getId());

        PostingResult posting = ledgerApi.post(new PostingRequest(
                "TRF:" + transfer.getId(),
                LedgerTransactionType.EXTERNAL_PAYOUT,
                "External payout " + source.accountNumber() + " -> "
                        + command.destinationBankCode() + "/" + command.destinationAccountNumber(),
                List.of(
                        PostingLine.debit(source.id(), amount),
                        PostingLine.credit(SystemAccounts.PAYSTACK_CLEARING_NGN, amount))));

        transfer.markProcessing(posting.transactionId(), clock.instant());
        return transfer;
    }

    private Transfer settle(UUID transferId, Outcome outcome) {
        return tx.execute(status -> applyOutcome(transferId, outcome));
    }

    /** Idempotent: safe to call from the response path, a webhook, a job, or a person. */
    private Transfer applyOutcome(UUID transferId, Outcome outcome) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSFER_NOT_FOUND", "No transfer with id " + transferId));

        if (transfer.getStatus() != TransferStatus.PROCESSING) {
            if (outcome.kind() == Outcome.Kind.REVERSE && transfer.getStatus() == TransferStatus.COMPLETED) {
                log.error("Provider reports transfer {} as failed or reversed, but it is already COMPLETED. "
                        + "Manual review needed; no automatic refund.", transfer.getId());
            }
            return transfer;   // somebody else already settled it
        }

        if (outcome.providerCode() != null) {
            transfer.recordProviderTransferCode(outcome.providerCode());
        }

        Instant now = clock.instant();
        switch (outcome.kind()) {
            case COMPLETED -> transfer.markCompleted(now);
            case REVERSE -> reverse(transfer, outcome.reason(), now);
            case STILL_PROCESSING -> {
                // nothing to change; a later refresh, webhook or job will settle it
            }
        }
        return transfer;
    }

    private void reverse(Transfer transfer, String reason, Instant now) {
        Money amount = transfer.getAmount();
        PostingResult reversal = ledgerApi.post(new PostingRequest(
                "TRF-REV:" + transfer.getId(),
                LedgerTransactionType.REVERSAL,
                "Reversal of transfer " + transfer.getId(),
                List.of(
                        PostingLine.debit(SystemAccounts.PAYSTACK_CLEARING_NGN, amount),
                        PostingLine.credit(transfer.getSourceAccountId(), amount))));
        transfer.reverse(reason, reversal.transactionId(), now);
    }

    private static Outcome outcomeOf(GatewayTransferResult result) {
        return switch (result.status()) {
            case SUCCESS -> Outcome.completed(result.providerCode());
            case FAILED -> Outcome.reverse("Provider reported the transfer as failed", result.providerCode());
            case REVERSED -> Outcome.reverse("Provider reversed the transfer", result.providerCode());
            case ACTION_REQUIRED -> Outcome.reverse(
                    "Provider requires OTP confirmation; disable OTP for API transfers", result.providerCode());
            case PENDING -> Outcome.stillProcessing(result.providerCode());
        };
    }

    private static void validateSource(AccountView source) {
        if (!"CUSTOMER".equals(source.accountType())) {
            throw new BusinessException("ACCOUNT_NOT_ELIGIBLE", "Only customer accounts can send transfers");
        }
        if (source.currency() != Currency.NGN) {
            throw new BusinessException("UNSUPPORTED_CURRENCY", "External transfers are NGN only");
        }
    }

    private static String reason(ExternalTransferCommand command) {
        return command.narration() == null || command.narration().isBlank()
                ? "PayBridge transfer"
                : command.narration();
    }

    private TransferResult replay(Transfer existing, ExternalTransferCommand command) {
        boolean sameRequest = existing.getType() == TransferType.EXTERNAL
                && existing.getSourceAccountNumber().equals(command.sourceAccountNumber())
                && existing.getDestinationAccountNumber().equals(command.destinationAccountNumber())
                && command.destinationBankCode().equals(existing.getDestinationBankCode())
                && existing.getAmount().toMajor().compareTo(command.amount()) == 0;
        if (!sameRequest) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key " + command.idempotencyKey() + " was already used for a different transfer");
        }
        return new TransferResult(TransferService.toView(existing), true);
    }

    /** What we decided to do after hearing from the provider. */
    private record Outcome(Kind kind, String reason, String providerCode) {

        enum Kind { COMPLETED, REVERSE, STILL_PROCESSING }

        static Outcome completed(String providerCode) {
            return new Outcome(Kind.COMPLETED, null, providerCode);
        }

        static Outcome reverse(String reason, String providerCode) {
            return new Outcome(Kind.REVERSE, reason, providerCode);
        }

        static Outcome stillProcessing(String providerCode) {
            return new Outcome(Kind.STILL_PROCESSING, null, providerCode);
        }
    }
}