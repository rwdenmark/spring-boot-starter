package com.example.starter.user;

import com.example.starter.common.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice test — fast, no Spring Boot context, no database.
 * Imports the real SecurityConfig so authorization rules are exercised.
 * Service layer is mocked.
 */
@WebMvcTest(UserController.class)
@Import(com.example.starter.config.SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean JpaUserDetailsService userDetailsService; // required by SecurityConfig

    @Test
    void create_isPublic_returnsCreated() throws Exception {
        when(userService.create(any())).thenReturn(
                new UserResponse(1L, "a@b.com", "Alice", "USER", Instant.parse("2026-01-01T00:00:00Z")));

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","name":"Alice","password":"supersecret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.email").value("a@b.com"));
    }

    @Test
    void create_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","name":"Alice","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"a@b.com","name":"Alice","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void findAll_authenticated_returnsPage() throws Exception {
        var pageable = PageRequest.of(0, 20);
        when(userService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(new UserResponse(1L, "a@b.com", "A", "USER", Instant.now())),
                pageable, 1));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void findById_notFound_returns404() throws Exception {
        when(userService.findById(99L)).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void update_authenticated_appliesPartialChanges() throws Exception {
        when(userService.update(eq(1L), any())).thenReturn(
                new UserResponse(1L, "a@b.com", "Alice 2", "USER", Instant.now()));

        mockMvc.perform(patch("/api/users/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Alice 2"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice 2"));
    }

    @Test
    void update_withoutAuth_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"x"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(delete("/api/users/1").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
