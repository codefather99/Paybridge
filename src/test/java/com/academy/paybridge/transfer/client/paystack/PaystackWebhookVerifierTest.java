package com.academy.paybridge.transfer.client.paystack;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackWebhookVerifierTest {

    private static final String SECRET = "sk_test_unit_test_secret";
    private static final byte[] BODY =
            "{\"event\":\"transfer.success\",\"data\":{\"reference\":\"trf_1\"}}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsASignatureMadeFromTheSameBodyAndKey() {
        String signature = PaystackWebhookVerifier.sign(BODY, SECRET);

        assertThat(PaystackWebhookVerifier.matches(BODY, signature, SECRET)).isTrue();
    }

    @Test
    void rejectsABodyThatWasTamperedWith() {
        String signature = PaystackWebhookVerifier.sign(BODY, SECRET);
        byte[] tampered = "{\"event\":\"transfer.failed\",\"data\":{\"reference\":\"trf_1\"}}"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(PaystackWebhookVerifier.matches(tampered, signature, SECRET)).isFalse();
    }

    @Test
    void rejectsASignatureMadeWithADifferentKey() {
        String signature = PaystackWebhookVerifier.sign(BODY, "sk_test_some_other_key");

        assertThat(PaystackWebhookVerifier.matches(BODY, signature, SECRET)).isFalse();
    }

    @Test
    void signatureIsLowercaseHexOfASha512Digest() {
        String signature = PaystackWebhookVerifier.sign(BODY, SECRET);

        assertThat(signature).hasSize(128).matches("[0-9a-f]+");
    }
}