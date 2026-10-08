package com.academy.paybridge.transfer.client;

import com.academy.paybridge.shared.money.Money;

import java.util.List;
import java.util.Optional;

/** What PayBridge needs from any external payment provider. */
public interface TransferGateway {

    List<Bank> listBanks();

    /** Name enquiry: who owns this account? */
    ResolvedAccount resolveAccount(String accountNumber, String bankCode);

    /** Registers a beneficiary with the provider and returns the provider's recipient code. */
    String createRecipient(String accountName, String accountNumber, String bankCode);

    GatewayTransferResult initiateTransfer(GatewayTransferRequest request);

    /** Looks a transfer up by our reference. Empty means the provider has no record of it. */
    Optional<GatewayTransferResult> verifyTransfer(String reference);

    record Bank(String name, String code) {
    }

    record ResolvedAccount(String accountNumber, String accountName) {
    }

    record GatewayTransferRequest(String reference, String recipientCode, Money amount, String reason) {
    }

    record GatewayTransferResult(String reference, String providerCode,
                                 GatewayTransferStatus status, String message) {
    }

    enum GatewayTransferStatus {
        SUCCESS,
        PENDING,          // accepted, outcome not final yet (also our default for anything unrecognised)
        FAILED,
        REVERSED,
        ACTION_REQUIRED   // provider wants a human step, such as an OTP
    }
}