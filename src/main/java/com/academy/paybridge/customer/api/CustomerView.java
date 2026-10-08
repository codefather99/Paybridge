package com.academy.paybridge.customer.api;

import java.util.UUID;

public record CustomerView(UUID id, String fullName) {
}