package com.example.starter.auth;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.starter.config.RateLimitingFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController}. AuthenticationManager and
 * TokenService are mocked, so this pins request mapping, validation, and the
 * BadCredentialsException-to-401 translation in GlobalExceptionHandler.
 * JwtAuthIT covers the same flow end to end with real tokens.
 */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitingFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean TokenService tokenService;

    private Authentication authenticated(String email) {
        return UsernamePasswordAuthenticationToken.authenticated(
                email, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void login_happyPath_returns200WithTokenAndExpiry() throws Exception {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authenticated("alice@example.com"));
        when(tokenService.issue(any(Authentication.class)))
                .thenReturn(new TokenResponse("signed-jwt", Instant.parse("2026-01-01T01:00:00Z")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"supersecret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-01T01:00:00Z"));
    }

    @Test
    void login_wrongPassword_returns401WithVagueDetail() throws Exception {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_invalidEmail_returns400_andSkipsAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"supersecret"}
                                """))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_blankPassword_returns400_andSkipsAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":""}
                                """))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }
}
