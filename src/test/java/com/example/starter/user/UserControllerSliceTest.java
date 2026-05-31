package com.example.starter.user;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.starter.common.NotFoundException;
import com.example.starter.config.RateLimitingFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link UserController}.
 *
 * Loads only the web layer for this controller. {@link UserService} is mocked,
 * the database is not touched, and security filters are disabled so this test
 * focuses purely on request mapping, validation, JSON serialization, and the
 * exception-to-status translation in {@link com.example.starter.common.GlobalExceptionHandler}.
 *
 * Companion to {@link UserControllerIT}, which is the full @SpringBootTest +
 * Testcontainers integration test. Use the IT for end-to-end behavior; use this
 * slice test for fast feedback on controller-only changes.
 */
@WebMvcTest(controllers = UserController.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitingFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class UserControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    private UserResponse aliceResponse() {
        return new UserResponse(1L, "alice@example.com", "Alice", "USER",
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    // --- POST /api/users ---

    @Test
    void create_happyPath_returns201WithBodyAndLocationHeader() throws Exception {
        when(userService.create(any(CreateUserRequest.class))).thenReturn(aliceResponse());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","name":"Alice","password":"supersecret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/users/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void create_invalidEmail_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","name":"Alice","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("email")));

        verify(userService, never()).create(any());
    }

    @Test
    void create_passwordTooShort_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","name":"Alice","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).create(any());
    }

    @Test
    void create_duplicateEmail_returns400_whenServiceThrowsIllegalArgument() throws Exception {
        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Email already in use"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","name":"Dup","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email already in use"));
    }

    // --- GET /api/users/{id} ---

    @Test
    void findById_returns200WithUser() throws Exception {
        when(userService.findById(1L)).thenReturn(aliceResponse());

        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void findById_notFound_returns404_whenServiceThrowsNotFound() throws Exception {
        when(userService.findById(99L)).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found: 99"));
    }

    // --- GET /api/users ---

    @Test
    void findAll_returnsPagedResult() throws Exception {
        var pageable = PageRequest.of(0, 20);
        Page<UserResponse> page = new PageImpl<>(List.of(aliceResponse()), pageable, 1);
        when(userService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    // --- PATCH /api/users/{id} ---

    @Test
    void update_partialUpdate_returns200() throws Exception {
        var updated = new UserResponse(1L, "alice@example.com", "Alice Updated", "USER",
                Instant.parse("2026-01-01T00:00:00Z"));
        when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(updated);

        mockMvc.perform(patch("/api/users/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void update_notFound_returns404_whenServiceThrowsNotFound() throws Exception {
        when(userService.update(eq(99L), any(UpdateUserRequest.class)))
                .thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(patch("/api/users/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Whatever"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/users/{id} ---

    @Test
    void delete_returns204_andCallsService() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }

    @Test
    void delete_notFound_returns404_whenServiceThrowsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("User not found: 99"))
                .when(userService).delete(99L);

        mockMvc.perform(delete("/api/users/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
