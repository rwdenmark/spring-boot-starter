package com.example.starter.auth;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.example.starter.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue/parse round trip with a real Nimbus encoder and decoder on a fixed
 * key. No Spring context, runs in milliseconds.
 */
class TokenServiceTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    private TokenService tokenService(long expiryMinutes) {
        var props = new AppProperties(null, null, null, null, null,
                new AppProperties.Jwt("irrelevant-here", expiryMinutes));
        return new TokenService(encoder, props);
    }

    private Authentication authenticated(String email, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void issue_roundTrip_subjectSurvives() {
        var issued = tokenService(60).issue(authenticated("alice@example.com", "USER"));

        var jwt = decoder.decode(issued.token());

        assertThat(jwt.getSubject()).isEqualTo("alice@example.com");
    }

    @Test
    void issue_roleClaimCarriesTheStrippedAuthority() {
        var userToken = tokenService(60).issue(authenticated("alice@example.com", "USER"));
        var adminToken = tokenService(60).issue(authenticated("root@example.com", "ADMIN"));

        assertThat(decoder.decode(userToken.token()).getClaimAsString("role")).isEqualTo("USER");
        assertThat(decoder.decode(adminToken.token()).getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void issue_expiryMatchesConfiguredMinutes() {
        var before = Instant.now();
        var issued = tokenService(30).issue(authenticated("alice@example.com", "USER"));
        var after = Instant.now();

        var jwt = decoder.decode(issued.token());

        // JWT timestamps have second precision, so compare truncated values.
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS));
        assertThat(issued.expiresAt())
                .isAfterOrEqualTo(before.plus(30, ChronoUnit.MINUTES))
                .isBeforeOrEqualTo(after.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    void decode_expiredToken_isRejected() {
        // Encoded directly so the expiry sits well past the decoder's 60s clock skew.
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject("alice@example.com")
                .issuedAt(now.minus(20, ChronoUnit.MINUTES))
                .expiresAt(now.minus(10, ChronoUnit.MINUTES))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var expired = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(expired))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void decode_tokenSignedWithDifferentKey_isRejected() {
        var otherKey = new SecretKeySpec(
                "another-secret-key-of-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        var forged = new TokenService(otherEncoder,
                new AppProperties(null, null, null, null, null, new AppProperties.Jwt(null, 60)))
                .issue(authenticated("alice@example.com", "USER"));

        assertThatThrownBy(() -> decoder.decode(forged.token()))
                .isInstanceOf(JwtException.class);
    }
}
