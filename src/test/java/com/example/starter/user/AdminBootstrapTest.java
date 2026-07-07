package com.example.starter.user;

import com.example.starter.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private final MockEnvironment environment = new MockEnvironment();

    private AdminBootstrap bootstrap(String email, String password) {
        var props = new AppProperties(null, new AppProperties.Admin(email, password), null, null, null, null);
        return new AdminBootstrap(userRepository, passwordEncoder, props, environment);
    }

    @Test
    void run_prodProfileWithDefaultPassword_refusesToStart() {
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> bootstrap("admin@example.com", "changeme").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_devWithDefaultPassword_warnsAndStillBootstraps() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("changeme")).thenReturn("{bcrypt}hashed");

        assertThatCode(() -> bootstrap("admin@example.com", "changeme").run())
                .doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void run_prodWithRealPassword_bootstraps() {
        environment.setActiveProfiles("prod");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-real-secret")).thenReturn("{bcrypt}hashed");

        assertThatCode(() -> bootstrap("admin@example.com", "a-real-secret").run())
                .doesNotThrowAnyException();

        verify(userRepository).save(any(User.class));
    }

    @Test
    void run_adminAlreadyExists_skipsBootstrap() {
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(new User("admin@example.com", "Admin", "{bcrypt}hashed")));

        bootstrap("admin@example.com", "a-real-secret").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_concurrentBootstrapRace_swallowsUniqueViolation() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-real-secret")).thenReturn("{bcrypt}hashed");
        // Another instance won the race between the existence check and the save.
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatCode(() -> bootstrap("admin@example.com", "a-real-secret").run())
                .doesNotThrowAnyException();
    }

    @Test
    void run_missingConfig_skipsBootstrap() {
        bootstrap(null, null).run();

        verify(userRepository, never()).save(any());
    }
}
