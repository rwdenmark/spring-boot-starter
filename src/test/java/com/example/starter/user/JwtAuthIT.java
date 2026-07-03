package com.example.starter.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round trip through the JWT filter chain. Login mints a real
 * HS256 token against the BCrypt hash stored in Postgres, then the bearer
 * token drives the protected endpoints, including ownership on PATCH with
 * two real users and the admin-only DELETE via the bootstrapped admin.
 * Runs on a random port with no MockMvc shortcuts, so Docker is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JwtAuthIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void usePatchCapableClient() {
        // HttpURLConnection cannot send PATCH. The JDK HttpClient factory can.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private HttpHeaders jsonHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Long registerUser(String email) throws Exception {
        var body = """
                {"email":"%s","name":"Carol","password":"supersecret"}
                """.formatted(email);
        var created = restTemplate.postForEntity("/api/users",
                new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(created.getBody()).get("id").asLong();
    }

    private ResponseEntity<String> login(String email, String password) {
        var body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()), String.class);
    }

    private String token(String email, String password) throws Exception {
        var response = login(email, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private HttpHeaders bearer(String token) {
        var headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void login_withDbBackedCredentials_returnsTokenAndFutureExpiry() throws Exception {
        registerUser("carol@example.com");

        var response = login("carol@example.com", "supersecret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var json = objectMapper.readTree(response.getBody());
        assertThat(json.get("token").asText()).isNotBlank();
        assertThat(Instant.parse(json.get("expiresAt").asText())).isAfter(Instant.now());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        registerUser("dave@example.com");

        var response = login("dave@example.com", "wrong-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withMissingPassword_returns400() {
        var response = restTemplate.postForEntity("/api/auth/login",
                new HttpEntity<>("""
                        {"email":"nobody@example.com"}
                        """, jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listUsers_withBearerToken_returns200() throws Exception {
        registerUser("erin@example.com");
        var token = token("erin@example.com", "supersecret");

        var response = restTemplate.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listUsers_withoutToken_returns401() {
        var response = restTemplate.getForEntity("/api/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void patch_ownershipEnforced_betweenTwoRealUsers() throws Exception {
        Long frankId = registerUser("frank@example.com");
        registerUser("grace@example.com");
        var frankToken = token("frank@example.com", "supersecret");
        var graceToken = token("grace@example.com", "supersecret");
        var rename = """
                {"name":"Renamed"}
                """;

        var denied = restTemplate.exchange("/api/users/" + frankId, HttpMethod.PATCH,
                new HttpEntity<>(rename, bearer(graceToken)), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var allowed = restTemplate.exchange("/api/users/" + frankId, HttpMethod.PATCH,
                new HttpEntity<>(rename, bearer(frankToken)), String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(allowed.getBody()).get("name").asText()).isEqualTo("Renamed");
    }

    @Test
    void delete_requiresAdminRoleFromTokenClaim() throws Exception {
        Long heidiId = registerUser("heidi@example.com");
        var heidiToken = token("heidi@example.com", "supersecret");
        // AdminBootstrap seeds this account from ADMIN_EMAIL/ADMIN_PASSWORD defaults.
        var adminToken = token("admin@example.com", "changeme");

        var denied = restTemplate.exchange("/api/users/" + heidiId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(heidiToken)), String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var deleted = restTemplate.exchange("/api/users/" + heidiId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(adminToken)), String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
