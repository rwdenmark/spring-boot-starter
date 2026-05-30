package com.example.starter.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    @Test
    void createUser_returnsCreated_andHidesPassword() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","name":"Alice","password":"supersecret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","name":"Bob","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_duplicateEmail_returns400() throws Exception {
        createUser("dup@example.com", "First");

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","name":"Second","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getUserById_returnsUser() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getAllUsers_returnsPagedResult() throws Exception {
        createUser("a@example.com", "A");
        createUser("b@example.com", "B");
        // AdminBootstrap seeds one admin at startup; the test adds two more.
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @WithMockUser
    void updateUser_partialUpdate_keepsOtherFields() throws Exception {
        Long id = createUser("alice@example.com", "Alice");

        mockMvc.perform(patch("/api/users/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser
    void updateUser_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/users/{id}", 999_999_999L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Whatever"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_asAdmin_returns204_andGetReturns404() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(delete("/api/users/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteUser_asUser_returns403() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(delete("/api/users/{id}", id).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
