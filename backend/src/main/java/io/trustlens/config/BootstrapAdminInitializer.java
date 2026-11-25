package io.trustlens.config;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-time first-admin bootstrap. It never exposes an administrator signup API
 * and never overwrites an existing administrator's credentials.
 */
@Component
@Profile("prod")
@ConditionalOnProperty(name="app.bootstrap-admin.enabled", havingValue="true")
public class BootstrapAdminInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    @Value("${BOOTSTRAP_ADMIN_USERNAME:}") private String username;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") private String password;
    @Value("${BOOTSTRAP_ADMIN_DISPLAY_NAME:Campus Security Administrator}") private String displayName;
    @Value("${BOOTSTRAP_ADMIN_DEPARTMENT:IT}") private String department;

    public BootstrapAdminInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        Integer admins = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            WHERE r.code='SECURITY_ADMIN'
            """, Integer.class);
        if (admins != null && admins > 0) return;
        if (username == null || !username.matches("[a-zA-Z0-9._-]{3,80}") ||
            password == null || password.length() < 16)
            throw new IllegalStateException("Set BOOTSTRAP_ADMIN_USERNAME and a unique BOOTSTRAP_ADMIN_PASSWORD of at least 16 characters for first startup.");
        jdbc.update("INSERT INTO app_roles(id,code,description) VALUES (?, 'SECURITY_ADMIN','Security administrator') ON CONFLICT(code) DO NOTHING",
            UUID.randomUUID());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO app_users(id,username,password_hash,display_name,department_code,enabled)
            VALUES (?,?,?,?,?,TRUE)
            """, id, username.toLowerCase(), encoder.encode(password), displayName, department);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE code='SECURITY_ADMIN'", id);
    }
}
