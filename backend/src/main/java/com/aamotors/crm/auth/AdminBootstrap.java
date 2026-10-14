package com.aamotors.crm.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the single ADMIN account on first start from environment variables.
 * Refuses to start when no admin exists and no bootstrap credentials are provided,
 * because the application would otherwise be impossible to log into.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final AdminUserRepository users;
    private final AdminProperties properties;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AdminUserRepository users, AdminProperties properties, PasswordEncoder encoder) {
        this.users = users;
        this.properties = properties;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (!properties.isConfigured()) {
            throw new IllegalStateException("""
                    No administrator account exists and ADMIN_USERNAME / ADMIN_INITIAL_PASSWORD are not set.
                    Set both environment variables for the first start, then change the password from the UI.""");
        }
        if (properties.initialPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_INITIAL_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        AdminUser admin = new AdminUser(properties.username().trim(), encoder.encode(properties.initialPassword()), true);
        users.save(admin);
        log.info("Created initial ADMIN user '{}' from environment. Password change is required on first login.", admin.getUsername());
    }
}
