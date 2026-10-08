package com.academy.paybridge.transfer.client.paystack;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "paystack")
public record PaystackProperties(
        String baseUrl,
        String secretKey,
        Duration connectTimeout,
        Duration readTimeout) {

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    // Records generate a toString() that prints every field. Never let the key reach a log.
    @Override
    public String toString() {
        return "PaystackProperties[baseUrl=" + baseUrl + ", secretKey=****]";
    }
}