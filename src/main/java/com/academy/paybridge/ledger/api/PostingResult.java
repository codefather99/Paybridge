package com.academy.paybridge.ledger.api;

import java.util.UUID;

/** Outcome of a posting. replayed = true means this reference was already posted. */
public record PostingResult(UUID transactionId, String reference, boolean replayed) {
}