package io.trustlens.config;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        String facultyPassword = requiredDemoPassword("DEMO_FACULTY_PASSWORD");
        String adminPassword = requiredDemoPassword("DEMO_ADMIN_PASSWORD");
        ensureRole("FACULTY", "Faculty member");
        ensureRole("SECURITY_ADMIN", "Security administrator");
        UUID demoId = ensureUser("demo", "Demo Faculty", "CSE", facultyPassword);
        UUID adminId = ensureUser("admin", "Demo Security Administrator", "IT", adminPassword);
        assignRole(demoId, "FACULTY");
        assignRole(adminId, "SECURITY_ADMIN");

        UUID docId = jdbc.query("SELECT id FROM documents WHERE title = ?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, "TRUSTLENS Demo Research Proposal");
        if (docId == null) {
            docId = UUID.randomUUID();
            jdbc.update("INSERT INTO documents(id,title,owner_id,department_code,classification,storage_key,status) VALUES (?,?,?,?,?,?,?)",
                docId, "TRUSTLENS Demo Research Proposal", adminId, "CSE", "CONFIDENTIAL",
                "demo://research-proposal", "ACTIVE");
        }
        Integer grants = jdbc.queryForObject(
            "SELECT COUNT(*) FROM access_grants WHERE document_id=? AND subject_user_id=? AND permission='READ' AND revoked_at IS NULL",
            Integer.class, docId, demoId);
        if (grants == null || grants == 0) {
            jdbc.update("INSERT INTO access_grants(id,document_id,subject_user_id,permission,granted_by) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), docId, demoId, "READ", adminId);
        }
    }

    private String requiredDemoPassword(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.length() < 14) {
            throw new IllegalStateException(variable + " must be set to a unique password of at least 14 characters when demo seeding is enabled");
        }
        return value;
    }

    private void ensureRole(String code, String description) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE code=?", Integer.class, code);
        if (count == null || count == 0) jdbc.update("INSERT INTO app_roles(id,code,description) VALUES (?,?,?)",
            UUID.randomUUID(), code, description);
    }

    private UUID ensureUser(String username, String displayName, String department, String password) {
        UUID existing = jdbc.query("SELECT id FROM app_users WHERE username=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, username);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(id,username,password_hash,display_name,department_code,enabled) VALUES (?,?,?,?,?,TRUE)",
            id, username, encoder.encode(password), displayName, department);
        return id;
    }

    private void assignRole(UUID userId, String roleCode) {
        jdbc.update("""
            INSERT INTO user_roles(user_id,role_id)
            SELECT ?, id FROM app_roles WHERE code=?
            ON CONFLICT DO NOTHING
            """, userId, roleCode);
    }
}
