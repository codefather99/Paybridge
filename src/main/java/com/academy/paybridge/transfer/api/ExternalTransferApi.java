package com.academy.paybridge.transfer.api;

import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;

import java.util.Optional;
import java.util.UUID;

public interface ExternalTransferApi {

    TransferResult transferExternal(ExternalTransferCommand command);

    /** Asks the provider what really happened to a PROCESSING transfer and settles it. */
    TransferView refresh(UUID transferId);

    /**
     * Applies a status the provider reported (webhook). Idempotent.
     * Empty means we have no transfer with that provider reference.
     * Throws AMOUNT_MISMATCH if the reported amount differs from ours.
     */
    Optional<TransferView> applyProviderUpdate(String providerReference, GatewayTransferStatus status,
                                               String providerCode, Long reportedAmountMinor);
}