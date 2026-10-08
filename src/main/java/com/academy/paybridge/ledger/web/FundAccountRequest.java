package com.academy.paybridge.ledger.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FundAccountRequest(
        @NotBlank String accountNumber,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") BigDecimal amount) {
}