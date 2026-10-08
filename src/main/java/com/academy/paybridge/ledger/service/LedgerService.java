package com.academy.paybridge.ledger.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.ledger.api.EntryDirection;
import com.academy.paybridge.ledger.api.LedgerApi;
import com.academy.paybridge.ledger.api.PostingLine;
import com.academy.paybridge.ledger.api.PostingRequest;
import com.academy.paybridge.ledger.api.PostingResult;
import com.academy.paybridge.ledger.domain.LedgerEntry;
import com.academy.paybridge.ledger.domain.LedgerTransaction;
import com.academy.paybridge.ledger.repository.LedgerEntryRepository;
import com.academy.paybridge.ledger.repository.LedgerTransactionRepository;
import com.academy.paybridge.shared.exception.BusinessException;
import com.academy.paybridge.shared.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LedgerService implements LedgerApi {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountApi accountApi;
    private final Clock clock;

    public LedgerService(LedgerTransactionRepository transactionRepository,
                         LedgerEntryRepository entryRepository,
                         AccountApi accountApi,
                         Clock clock) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.accountApi = accountApi;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PostingResult post(PostingRequest request) {
        Optional<LedgerTransaction> existing = transactionRepository.findByReference(request.reference());
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        Instant now = clock.instant();

        // Flush immediately: if a concurrent request already used this reference, the UNIQUE
        // constraint fails right here, before we lock any accounts.
        LedgerTransaction transaction =
                transactionRepository.saveAndFlush(LedgerTransaction.from(request, now));

        // Always lock accounts in the same global order to avoid deadlocks.
        List<PostingLine> ordered = request.lines().stream()
                .sorted(Comparator.comparing(PostingLine::accountId))
                .toList();

        List<LedgerEntry> entries = new ArrayList<>();
        for (PostingLine line : ordered) {
            Money balanceAfter = line.direction() == EntryDirection.CREDIT
                    ? accountApi.credit(line.accountId(), line.amount())
                    : accountApi.debit(line.accountId(), line.amount());
            entries.add(LedgerEntry.of(transaction.getId(), line.accountId(),
                    line.direction(), line.amount(), balanceAfter, now));
        }
        entryRepository.saveAll(entries);

        return new PostingResult(transaction.getId(), transaction.getReference(), false);
    }

    private PostingResult replay(LedgerTransaction existing, PostingRequest request) {
        Set<String> stored = entryRepository.findByTransactionId(existing.getId()).stream()
                .map(e -> fingerprint(e.getAccountId(), e.getDirection(), e.getAmount()))
                .collect(Collectors.toSet());
        Set<String> requested = request.lines().stream()
                .map(l -> fingerprint(l.accountId(), l.direction(), l.amount()))
                .collect(Collectors.toSet());

        if (existing.getType() != request.type() || !stored.equals(requested)) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT",
                    "Reference " + request.reference() + " was already used for a different posting");
        }
        return new PostingResult(existing.getId(), existing.getReference(), true);
    }

    private static String fingerprint(UUID accountId, EntryDirection direction, Money amount) {
        return accountId + "|" + direction + "|" + amount.minorUnits() + "|" + amount.currency();
    }
}