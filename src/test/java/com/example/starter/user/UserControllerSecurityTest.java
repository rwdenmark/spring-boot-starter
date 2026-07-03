package com.example.starter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.starter.config.AppProperties;
import com.example.starter.config.RateLimitingFilter;
import com.example.starter.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the security rules on {@link UserController}. Unlike
 * {@link UserControllerSliceTest} this one keeps the security filter chain
 * enabled and imports the real {@link SecurityConfig}, so it pins the
 * anonymous-request behavior without needing a database or Docker.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitingFilter.class))
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
class UserControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void listUsers_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchUser_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/{id}", 1L)
                        .contentType("application/json")
                        .content("""
                                {"name":"Nope"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
