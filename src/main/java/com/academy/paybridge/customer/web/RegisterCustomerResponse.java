package com.academy.paybridge.customer.web;

import java.util.UUID;

public record RegisterCustomerResponse(UUID customerId, String fullName, String apiKey, String note) {
}