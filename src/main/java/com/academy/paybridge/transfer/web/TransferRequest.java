package com.academy.paybridge.transfer.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @Schema(description = "Account number to debit", example = "4017203596")
        @NotBlank String sourceAccountNumber,

        @Schema(description = "Account number to credit", example = "8820911537")
        @NotBlank String destinationAccountNumber,

        @Schema(description = "Amount in naira, at most 2 decimal places", example = "1500.00")
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") BigDecimal amount,

        @Schema(description = "Optional note shown on statements", example = "Rent contribution")
        @Size(max = 100) String narration) {
}