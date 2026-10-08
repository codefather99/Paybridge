package com.academy.paybridge.transfer.client.paystack;

import com.academy.paybridge.transfer.client.TransferGateway.GatewayTransferStatus;

import java.util.Locale;

final class PaystackStatusMapper {

    private PaystackStatusMapper() {
    }

    /** Anything we do not explicitly recognise is PENDING: never guess success or failure. */
    static GatewayTransferStatus map(String paystackStatus) {
        if (paystackStatus == null) {
            return GatewayTransferStatus.PENDING;
        }
        return switch (paystackStatus.toLowerCase(Locale.ROOT)) {
            case "success" -> GatewayTransferStatus.SUCCESS;
            case "failed" -> GatewayTransferStatus.FAILED;
            case "reversed" -> GatewayTransferStatus.REVERSED;
            case "otp" -> GatewayTransferStatus.ACTION_REQUIRED;
            default -> GatewayTransferStatus.PENDING;
        };
    }
}