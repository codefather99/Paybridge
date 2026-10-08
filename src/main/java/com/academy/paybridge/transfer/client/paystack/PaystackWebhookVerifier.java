package com.academy.paybridge.transfer.client.paystack;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class PaystackWebhookVerifier {

    private static final String ALGORITHM = "HmacSHA512";

    private final PaystackProperties properties;

    public PaystackWebhookVerifier(PaystackProperties properties) {
        this.properties = properties;
    }

    /** True only if the header is the HMAC-SHA512 of exactly these bytes under our secret key. */
    public boolean isAuthentic(byte[] rawBody, String signatureHeader) {
        if (!properties.isConfigured() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        return matches(rawBody, signatureHeader, properties.secretKey());
    }

    static boolean matches(byte[] rawBody, String signatureHeader, String secret) {
        String expected = sign(rawBody, secret);
        String received = signatureHeader.trim().toLowerCase(Locale.ROOT);
        // Constant-time comparison: does not leak how many leading characters were right.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    static String sign(byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA512 is not available", e);
        }
    }
}