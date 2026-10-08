package com.academy.paybridge.shared.idempotency;

import com.academy.paybridge.shared.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class IdempotencyKeys {

    public static final int MAX_LENGTH = 80;

    private IdempotencyKeys() {
    }

    public static String requireValid(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1 to " + MAX_LENGTH + " characters");
        }
        return key;
    }

    /**
     * Makes the key private to one customer: two customers can send the same text without
     * ever colliding, and one can never replay another's request. Always 64 hex characters.
     */
    public static String scoped(UUID customerId, String clientKey) {
        requireValid(clientKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((customerId + ":" + clientKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}