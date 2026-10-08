package com.academy.paybridge.customer.api;

import com.academy.paybridge.shared.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedCustomer customer) {
            return customer.id();
        }
        throw new BusinessException("UNAUTHENTICATED", "Authentication is required");
    }
}