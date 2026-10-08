package com.academy.paybridge.transfer.api;

import com.academy.paybridge.shared.money.Money;

import java.time.Instant;
import java.util.UUID;

public record TransferView(
        UUID id,
        String type,
        String status,
        String sourceAccountNumber,
        String destinationAccountNumber,
        String destinationBankCode,
        String destinationAccountName,
        Money amount,
        String narration,
        String failureReason,
        Instant createdAt) {
}