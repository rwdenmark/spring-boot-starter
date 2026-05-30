package com.example.starter.user;

import com.example.starter.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures an ADMIN user exists on first startup so admin-only endpoints are
 * reachable. Reads app.admin.email / app.admin.password (env: ADMIN_EMAIL,
 * ADMIN_PASSWORD). Logs a warning and proceeds if the password is left as
 * the default "changeme".
 */
@Component
class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String UNSAFE_PASSWORD = "changeme";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    AdminBootstrap(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        var admin = appProperties.admin();
        if (admin == null || admin.email() == null || admin.password() == null) {
            log.warn("app.admin.email/password not configured; skipping admin bootstrap");
            return;
        }

        if (userRepository.findByEmail(admin.email()).isPresent()) {
            return;
        }

        if (UNSAFE_PASSWORD.equals(admin.password())) {
            log.warn("Bootstrapping admin with default password '{}'. Set ADMIN_PASSWORD before production.",
                    UNSAFE_PASSWORD);
        }

        var user = new User(admin.email(), "Admin", passwordEncoder.encode(admin.password()));
        user.setRole(User.Role.ADMIN);
        userRepository.save(user);
        log.info("Bootstrapped ADMIN user {}", admin.email());
    }
}
