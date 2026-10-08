package com.academy.paybridge.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "paybridge.transfers", name = "reconcile-enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}