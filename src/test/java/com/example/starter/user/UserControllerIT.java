package com.example.starter.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
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
@Transactional
class UserControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private Long createUser(String email, String name) throws Exception {
        var json = """
                { "email": "%s", "name": "%s", "password": "supersecret" }
                """.formatted(email, name);

        var result = mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

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

    @Test
    void getUserById_returnsUser() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsList() throws Exception {
        createUser("a@example.com", "A");
        createUser("b@example.com", "B");
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateUser_returnsUpdated() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        var json = """ 
                { "name": "Alice Updated" }
                """;
        mockMvc.perform(put("/api/users/{id}", id)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void updateUser_notFound_returns404() throws Exception {
        var json = """ 
                { "name": "Whatever" }
                """;
        mockMvc.perform(put("/api/users/{id}", 999_999_999L)
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_returns204_andGetReturns404() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound());
    }
}
