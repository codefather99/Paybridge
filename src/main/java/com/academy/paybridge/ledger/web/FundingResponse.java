package com.academy.paybridge.ledger.web;

import java.util.UUID;

public record FundingResponse(
        UUID transactionId,
        String reference,
        boolean replayed,
        String accountNumber,
        String balance) {
}