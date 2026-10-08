package com.academy.paybridge.transfer.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ExternalTransferRequest(
        @Schema(description = "PayBridge account to debit", example = "4017203596")
        @NotBlank String sourceAccountNumber,

        @Schema(description = "Recipient's 10-digit bank account number", example = "0123456789")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String destinationAccountNumber,

        @Schema(description = "Recipient's bank code from GET /api/v1/banks", example = "058")
        @NotBlank @Pattern(regexp = "\\d{3,6}", message = "must be 3 to 6 digits") String destinationBankCode,

        @Schema(description = "Amount in naira, at most 2 decimal places", example = "1500.00")
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") BigDecimal amount,

        @Schema(description = "Optional note", example = "Invoice 1042")
        @Size(max = 100) String narration) {
}