package com.example.starter.greeting;

import com.example.starter.config.AppProperties;
import com.example.starter.config.SecurityConfig;
import com.example.starter.user.JpaUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GreetingController.class)
@Import({SecurityConfig.class, GreetingControllerTest.TestConfig.class})
class GreetingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean JpaUserDetailsService userDetailsService; // required by SecurityConfig

    @Configuration
    static class TestConfig {
        @Bean
        AppProperties appProperties() {
            return new AppProperties("hello from test");
        }
    }

    @Test
    void greeting_isPublic_returnsConfiguredMessage() throws Exception {
        mockMvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("hello from test"));
    }
}
