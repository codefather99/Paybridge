package com.academy.paybridge.transfer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakeGatewayConfig {

    @Bean
    @Primary
    FakeTransferGateway fakeTransferGateway() {
        return new FakeTransferGateway();
    }
}