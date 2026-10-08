package com.academy.paybridge.ledger.repository;

import com.academy.paybridge.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    Optional<LedgerTransaction> findByReference(String reference);
}