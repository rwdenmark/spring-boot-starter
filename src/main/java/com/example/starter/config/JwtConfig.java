package com.example.starter.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * HS256 signing key plus the encoder/decoder pair built on it. The key comes
 * from app.jwt.secret (env: JWT_SECRET) and must be at least 32 bytes. Under
 * the prod profile a missing or short secret refuses to start, same pattern
 * as the AdminBootstrap changeme check. Everywhere else it falls back to an
 * ephemeral random key, so tokens die on restart.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties appProperties;
    private final Environment environment;

    public JwtConfig(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Bean
    public SecretKey jwtSecretKey() {
        var secret = appProperties.jwt().secret();
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length >= MIN_SECRET_BYTES) {
                return new SecretKeySpec(bytes, "HmacSHA256");
            }
        }
        // Validated at startup so a weak key never signs a token.
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "JWT_SECRET is unset or shorter than 32 bytes under the prod profile. Set JWT_SECRET to a random value of at least 32 bytes.");
        }
        log.warn("JWT_SECRET is unset or shorter than 32 bytes. Using an ephemeral key, so tokens die on restart. Set JWT_SECRET before production.");
        byte[] ephemeral = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(ephemeral);
        return new SecretKeySpec(ephemeral, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Maps the token's "role" claim back to a ROLE_* authority so
     * hasRole("ADMIN") in SecurityConfig keeps working.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
