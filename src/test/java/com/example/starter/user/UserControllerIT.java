package com.example.starter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test against a real Postgres in Docker.
 * No mocks for the database layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createUser_returnsCreated() throws Exception {
        var json = """
            {
              "email": "alice@example.com",
              "name": "Alice",
              "password": "supersecret"
            }
            """;

        mockMvc.perform(post("/api/users")
                .contentType("application/json")
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        var json = """
            {
              "email": "not-an-email",
              "name": "Bob",
              "password": "supersecret"
            }
            """;

        mockMvc.perform(post("/api/users")
                .contentType("application/json")
                .content(json))
            .andExpect(status().isBadRequest());
    }
}
