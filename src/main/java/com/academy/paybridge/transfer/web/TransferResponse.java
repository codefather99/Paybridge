package com.academy.paybridge.transfer.web;

import com.academy.paybridge.transfer.api.TransferResult;
import com.academy.paybridge.transfer.api.TransferView;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String type,
        String status,
        String sourceAccountNumber,
        String destinationAccountNumber,
        String destinationBankCode,
        String destinationAccountName,
        long amountMinor,
        String amount,
        String currency,
        String narration,
        String failureReason,
        Instant createdAt,
        boolean replayed) {

    static TransferResponse from(TransferResult result) {
        return from(result.transfer(), result.replayed());
    }

    static TransferResponse from(TransferView v, boolean replayed) {
        return new TransferResponse(
                v.id(), v.type(), v.status(),
                v.sourceAccountNumber(), v.destinationAccountNumber(),
                v.destinationBankCode(), v.destinationAccountName(),
                v.amount().minorUnits(),
                v.amount().toMajor().toPlainString(),
                v.amount().currency().name(),
                v.narration(), v.failureReason(), v.createdAt(), replayed);
    }
}