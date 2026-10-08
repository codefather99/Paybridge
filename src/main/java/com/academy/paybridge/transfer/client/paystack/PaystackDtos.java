package com.academy.paybridge.transfer.client.paystack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Shapes of Paystack's JSON responses. Unknown fields are ignored so new fields never break us. */
public final class PaystackDtos {

    private PaystackDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(boolean status, String message, T data, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String next) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(boolean status, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BankData(String name, String code, Boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResolveData(
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("account_name") String accountName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecipientData(@JsonProperty("recipient_code") String recipientCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferData(
            String reference,
            @JsonProperty("transfer_code") String transferCode,
            String status) {
    }
}