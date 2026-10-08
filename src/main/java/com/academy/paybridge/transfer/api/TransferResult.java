package com.academy.paybridge.transfer.api;

public record TransferResult(TransferView transfer, boolean replayed) {
}