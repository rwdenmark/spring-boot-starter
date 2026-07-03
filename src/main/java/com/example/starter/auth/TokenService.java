package com.example.starter.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.starter.config.AppProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Mints HS256 access tokens. Subject is the user's email, the single "role"
 * claim carries USER or ADMIN, and expiry comes from app.jwt.expiry-minutes
 * (env: JWT_EXPIRY_MINUTES). No refresh tokens on purpose. Clients log in
 * again when the token expires.
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration expiry;

    public TokenService(JwtEncoder jwtEncoder, AppProperties appProperties) {
        this.jwtEncoder = jwtEncoder;
        this.expiry = Duration.ofMinutes(appProperties.jwt().expiryMinutes());
    }

    public TokenResponse issue(Authentication authentication) {
        var now = Instant.now();
        var expiresAt = now.plus(expiry);
        var role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("USER");
        var claims = JwtClaimsSet.builder()
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", role)
                .build();
        // JwtEncoder defaults to RS256, so the HS256 header is explicit.
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, expiresAt);
    }
}
