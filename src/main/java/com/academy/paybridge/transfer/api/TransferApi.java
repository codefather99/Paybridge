package com.academy.paybridge.transfer.api;

import java.util.UUID;

public interface TransferApi {

    TransferResult transfer(TransferCommand command);

    TransferView getTransfer(UUID transferId);
}