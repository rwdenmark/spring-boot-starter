package com.example.starter.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the startup validation of the JWT secret, same shape as
 * AdminBootstrapTest pins the changeme check.
 */
class JwtConfigTest {

    private static final String GOOD_SECRET = "0123456789abcdef0123456789abcdef";

    private final MockEnvironment environment = new MockEnvironment();

    private JwtConfig config(String secret) {
        var props = new AppProperties(null, null, null, null, null,
                new AppProperties.Jwt(secret, 60));
        return new JwtConfig(props, environment);
    }

    @Test
    void prodProfileWithoutSecret_refusesToStart() {
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> config(null).jwtSecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void prodProfileWithShortSecret_refusesToStart() {
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> config("way-too-short").jwtSecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void prodProfileWithRealSecret_usesIt() {
        environment.setActiveProfiles("prod");

        var key = config(GOOD_SECRET).jwtSecretKey();

        assertThat(key.getEncoded()).isEqualTo(GOOD_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void devWithoutSecret_fallsBackToEphemeralKey() {
        var key = config(null).jwtSecretKey();

        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    void devEphemeralKeys_differPerStartup() {
        var first = config(null).jwtSecretKey();
        var second = config(null).jwtSecretKey();

        assertThat(first.getEncoded()).isNotEqualTo(second.getEncoded());
    }
}
