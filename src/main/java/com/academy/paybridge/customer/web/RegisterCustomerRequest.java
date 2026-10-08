package com.academy.paybridge.customer.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(
        @Schema(example = "Ada Lovelace") @NotBlank @Size(max = 150) String fullName,
        @Schema(example = "ada@example.com") @NotBlank @Email @Size(max = 254) String email) {
}