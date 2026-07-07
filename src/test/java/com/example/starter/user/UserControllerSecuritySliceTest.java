package com.example.starter.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.starter.ControllerSliceTest;
import com.example.starter.config.AppProperties;
import com.example.starter.config.JwtConfig;
import com.example.starter.config.SecurityConfig;
import com.example.starter.greeting.GreetingController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the security rules on {@link UserController}. Unlike
 * {@link UserControllerSliceTest} this one keeps the security filter chain
 * enabled and imports the real {@link SecurityConfig} and {@link JwtConfig},
 * so it pins anonymous-request and role behavior without a database or
 * Docker. Authenticated requests use spring-security-test's jwt()
 * post-processor, which injects the token past the decoder the same way the
 * resource-server chain would after validation. {@link GreetingController}
 * is in the slice too, so the public-endpoint rule is pinned here as well.
 */
@ControllerSliceTest({UserController.class, GreetingController.class})
@Import({SecurityConfig.class, JwtConfig.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.greeting=hello from test")
class UserControllerSecuritySliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    // SecurityConfig's AuthenticationManager bean needs a UserDetailsService.
    // The login flow itself is covered in AuthControllerSliceTest and JwtAuthIT.
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void listUsers_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/users/{id}", 1L)
                        .contentType("application/json")
                        .content("""
                                {"name":"Nope"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_withJwt_returns200() throws Exception {
        when(userService.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/users")
                        .with(jwt().jwt(j -> j.subject("alice@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteUser_withUserJwt_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L)
                        .with(jwt().jwt(j -> j.subject("alice@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUser_withAdminJwt_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L)
                        .with(jwt().jwt(j -> j.subject("root@example.com"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void greeting_withoutToken_isPublic_andReturnsConfiguredMessage() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello from test"));
    }
}
