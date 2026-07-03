package com.example.starter.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    // jwt() injects the token past the decoder, the same shape the
    // resource-server chain produces. The subject is the email because that
    // is what the ownership check reads. JwtAuthIT covers real signed tokens.
    private static RequestPostProcessor userJwt(String email) {
        return jwt().jwt(j -> j.subject(email))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(j -> j.subject("root@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Long createUser(String email, String name) throws Exception {
        var json = """
                { "email": "%s", "name": "%s", "password": "supersecret" }
                """.formatted(email, name);

        var result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private long countUsers() throws Exception {
        var result = mockMvc.perform(get("/api/users").with(userJwt("reader@example.com")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("totalElements").asLong();
    }

    @Test
    void createUser_returnsCreated_andHidesPassword() throws Exception {
        mockMvc.perform(post("/api/users")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","name":"Second","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserById_returnsUser() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(get("/api/users/{id}", id).with(userJwt("reader@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999_999_999L).with(userJwt("reader@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsPagedResult() throws Exception {
        // Relative to the starting count so this test does not care what
        // AdminBootstrap or other setup seeded.
        long before = countUsers();

        createUser("a@example.com", "A");
        createUser("b@example.com", "B");

        // jsonPath deserializes small numbers as Integer, so compare as int.
        mockMvc.perform(get("/api/users").with(userJwt("reader@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value((int) (before + 2)));
    }

    @Test
    void updateUser_ownAccount_partialUpdate_keepsOtherFields() throws Exception {
        Long id = createUser("alice@example.com", "Alice");

        mockMvc.perform(patch("/api/users/{id}", id)
                        .with(userJwt("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void updateUser_someoneElsesAccount_returns403() throws Exception {
        Long id = createUser("alice@example.com", "Alice");

        mockMvc.perform(patch("/api/users/{id}", id)
                        .with(userJwt("mallory@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacked"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_asAdmin_canUpdateAnyAccount() throws Exception {
        Long id = createUser("alice@example.com", "Alice");

        mockMvc.perform(patch("/api/users/{id}", id)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed By Admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed By Admin"));
    }

    @Test
    void updateUser_duplicateEmail_returns400() throws Exception {
        createUser("bob@example.com", "Bob");
        Long aliceId = createUser("alice@example.com", "Alice");

        mockMvc.perform(patch("/api/users/{id}", aliceId)
                        .with(userJwt("alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    @Test
    void updateUser_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/users/{id}", 999_999_999L)
                        .with(userJwt("reader@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Whatever"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_asAdmin_returns204_andGetReturns404() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(delete("/api/users/{id}", id).with(adminJwt()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{id}", id).with(adminJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_asUser_returns403() throws Exception {
        Long id = createUser("alice@example.com", "Alice");
        mockMvc.perform(delete("/api/users/{id}", id).with(userJwt("alice@example.com")))
                .andExpect(status().isForbidden());
    }
}
