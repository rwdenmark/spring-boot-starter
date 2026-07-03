package com.example.starter.user;

import com.example.starter.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates an ADMIN user on first startup if none exists, so admin-only
 * endpoints are reachable. Reads app.admin.email / app.admin.password (env: ADMIN_EMAIL,
 * ADMIN_PASSWORD). Logs a warning and proceeds if the password is left as
 * the default "changeme". Under the prod profile it refuses to start instead.
 */
@Component
class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String UNSAFE_PASSWORD = "changeme";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Environment environment;

    AdminBootstrap(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   AppProperties appProperties,
                   Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        var admin = appProperties.admin();
        if (admin == null || admin.email() == null || admin.password() == null) {
            log.warn("app.admin.email/password not configured; skipping admin bootstrap");
            return;
        }

        // Checked before the existence lookup so a prod restart with a stale
        // default password still fails fast.
        if (UNSAFE_PASSWORD.equals(admin.password())) {
            if (environment.matchesProfiles("prod")) {
                throw new IllegalStateException(
                        "Admin password is still the default under the prod profile. Set ADMIN_PASSWORD to a real value.");
            }
            log.warn("Admin password is the default '{}'. Set ADMIN_PASSWORD before production.",
                    UNSAFE_PASSWORD);
        }

        if (userRepository.findByEmail(admin.email()).isPresent()) {
            return;
        }

        var user = new User(admin.email(), "Admin", passwordEncoder.encode(admin.password()));
        user.setRole(User.Role.ADMIN);
        userRepository.save(user);
        log.info("Bootstrapped ADMIN user {}", admin.email());
    }
}
