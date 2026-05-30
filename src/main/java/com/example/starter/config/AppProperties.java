package com.example.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-level configuration. Bound from properties prefixed with `app.`
 * in application.yml. Picked up via @ConfigurationPropertiesScan on
 * StarterApplication.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String greeting) {}
