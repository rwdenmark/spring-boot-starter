package com.example.starter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP round trip through the security filter chain and
 * {@link JpaUserDetailsService}. Registers a user over the wire, then
 * authenticates with HTTP Basic against the BCrypt hash stored in Postgres.
 * Runs on a random port with no MockMvc shortcuts, so Docker is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BasicAuthIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate restTemplate;

    private void registerUser(String email) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = """
                {"email":"%s","name":"Carol","password":"supersecret"}
                """.formatted(email);

        var created = restTemplate.postForEntity("/api/users",
                new HttpEntity<>(body, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void basicAuth_withDbBackedCredentials_authenticates() {
        registerUser("carol@example.com");

        var response = restTemplate
                .withBasicAuth("carol@example.com", "supersecret")
                .getForEntity("/api/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void basicAuth_withWrongPassword_returns401() {
        registerUser("dave@example.com");

        var response = restTemplate
                .withBasicAuth("dave@example.com", "wrong-password")
                .getForEntity("/api/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void basicAuth_withoutCredentials_returns401() {
        var response = restTemplate.getForEntity("/api/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
