package com.academy.paybridge.transfer.client.paystack;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaystackProperties.class)
class PaystackConfig {
}