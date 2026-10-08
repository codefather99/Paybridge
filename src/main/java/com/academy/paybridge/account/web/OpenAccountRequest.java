package com.academy.paybridge.account.web;

import com.academy.paybridge.shared.money.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
        @Schema(description = "Account currency", example = "NGN") @NotNull Currency currency) {
}