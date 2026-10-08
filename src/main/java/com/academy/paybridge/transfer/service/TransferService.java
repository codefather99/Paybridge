package com.academy.paybridge.transfer.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountView;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.LedgerTransactionType;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.ledger.api.PostingResult;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.api.TransferApi;
import com.academy.paybridge.transfer.api.TransferCommand;
import com.academy.paybridge.transfer.api.TransferResult;
import com.academy.paybridge.transfer.api.TransferView;
import com.academy.paybridge.transfer.domain.Transfer;
import com.academy.paybridge.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService implements TransferApi {

    private final TransferRepository transferRepository;
    private final AccountApi accountApi;
    private final LedgerApi ledgerApi;
    private final Clock clock;

    public TransferService(TransferRepository transferRepository,
                           AccountApi accountApi,
                           LedgerApi ledgerApi,
                           Clock clock) {
        this.transferRepository = transferRepository;
        this.accountApi = accountApi;
        this.ledgerApi = ledgerApi;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TransferResult transfer(TransferCommand command) {
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        AccountView source = accountApi.getByAccountNumber(command.sourceAccountNumber());
        AccountView destination = accountApi.getByAccountNumber(command.destinationAccountNumber());
        validateParties(source, destination);

        Money amount = Money.ofMajor(command.amount(), source.currency());

        // Flush now so a concurrent duplicate key fails here, before any money moves.
        Transfer transfer = transferRepository.saveAndFlush(Transfer.initiateInternal(
                command.idempotencyKey(),
                source.id(), source.accountNumber(),
                destination.id(), destination.accountNumber(),
                amount, command.narration(), clock.instant()));

        PostingResult posting = ledgerApi.post(new PostingRequest(
                "TRF:" + transfer.getId(),
                LedgerTransactionType.TRANSFER,
                "Transfer " + source.accountNumber() + " -> " + destination.accountNumber(),
                List.of(
                        PostingLine.debit(source.id(), amount),
                        PostingLine.credit(destination.id(), amount))));

        transfer.complete(posting.transactionId(), clock.instant());
        return new TransferResult(toView(transfer), false);
    }

    @Override
    @Transactional(readOnly = true)
    public TransferView getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .map(TransferService::toView)
                .orElseThrow(() -> new BusinessException(
                        "TRANSFER_NOT_FOUND", "No transfer with id " + transferId));
    }

    private void validateParties(AccountView source, AccountView destination) {
        if (!"CUSTOMER".equals(source.accountType()) || !"CUSTOMER".equals(destination.accountType())) {
            throw new BusinessException("ACCOUNT_NOT_ELIGIBLE",
                    "Only customer accounts can take part in a transfer");
        }
        if (source.id().equals(destination.id())) {
            throw new BusinessException("SAME_ACCOUNT_TRANSFER",
                    "Source and destination accounts must be different");
        }
        if (source.currency() != destination.currency()) {
            throw new BusinessException("CURRENCY_MISMATCH",
                    "Cross-currency transfers are not supported on this route");
        }
    }

    private TransferResult replay(Transfer existing, TransferCommand command) {
        boolean sameRequest =
                existing.getSourceAccountNumber().equals(command.sourceAccountNumber())
                        && existing.getDestinationAccountNumber().equals(command.destinationAccountNumber())
                        && existing.getAmount().toMajor().compareTo(command.amount()) == 0;
        if (!sameRequest) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key " + command.idempotencyKey() + " was already used for a different transfer");
        }
        return new TransferResult(toView(existing), true);
    }

    static TransferView toView(Transfer t) {
        return new TransferView(
                t.getId(),
                t.getType().name(),
                t.getStatus().name(),
                t.getSourceAccountNumber(),
                t.getDestinationAccountNumber(),
                t.getDestinationBankCode(),
                t.getDestinationAccountName(),
                t.getAmount(),
                t.getNarration(),
                t.getFailureReason(),
                t.getCreatedAt());
    }
}