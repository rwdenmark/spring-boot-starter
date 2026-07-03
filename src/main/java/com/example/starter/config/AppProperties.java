package com.example.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application-level configuration. Bound from properties prefixed with `app.`
 * in application.yml. Picked up via @ConfigurationPropertiesScan on
 * StarterApplication.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String greeting,
        Admin admin,
        Cors cors,
        RateLimit rateLimit,
        Security security
) {
    public AppProperties {
        if (admin == null) admin = new Admin(null, null);
        if (cors == null) cors = new Cors(List.of());
        if (rateLimit == null) rateLimit = new RateLimit(100, false);
        if (security == null) security = new Security(10);
    }

    public record Admin(String email, String password) {}

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public record RateLimit(int registrationRequestsPerMinute, boolean trustForwardedFor) {}

    public record Security(int bcryptStrength) {}
}
