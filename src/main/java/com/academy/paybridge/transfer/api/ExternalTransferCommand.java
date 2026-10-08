package com.academy.paybridge.transfer.api;

import java.math.BigDecimal;

public record ExternalTransferCommand(
        String idempotencyKey,
        String sourceAccountNumber,
        String destinationAccountNumber,
        String destinationBankCode,
        BigDecimal amount,
        String narration) {
}