package com.academy.paybridge.transfer.api;

import java.math.BigDecimal;

/** Amount is in major units (naira), e.g. 1500.50. */
public record TransferCommand(
        String idempotencyKey,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        String narration) {
}