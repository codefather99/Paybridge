package com.academy.paybridge.customer.api;

import java.util.UUID;

/** The only time the plain API key is ever available. */
public record RegisteredCustomer(UUID customerId, String fullName, String apiKey) {
}