package com.academy.paybridge.transfer.domain;

public enum TransferStatus {
    PENDING,      // created, nothing has moved yet
    PROCESSING,   // money is held; waiting on an external party
    COMPLETED,    // done
    FAILED,       // rejected before any money was committed
    REVERSED;     // money was taken, the external leg failed, and we put it back

    public boolean canMoveTo(TransferStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING || next == COMPLETED || next == FAILED;
            case PROCESSING -> next == COMPLETED || next == REVERSED;
            case COMPLETED -> next == REVERSED;   // late reversals do happen in real payment rails
            case FAILED, REVERSED -> false;
        };
    }
}