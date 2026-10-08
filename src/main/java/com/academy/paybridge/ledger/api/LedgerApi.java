package com.academy.paybridge.ledger.api;

public interface LedgerApi {

    PostingResult post(PostingRequest request);
}