package com.academy.paybridge.customer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class ApiKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    /** 32 random bytes (256 bits) in URL-safe text, with a recognisable prefix. */
    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "pbk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}