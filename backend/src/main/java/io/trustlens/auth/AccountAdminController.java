package io.trustlens.auth;

import io.trustlens.audit.IdentityAuditService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AccountAdminController {
    private final JdbcTemplate jdbc;
    private final IdentityAuditService audit;

    public AccountAdminController(JdbcTemplate jdbc, IdentityAuditService audit) {
        this.jdbc = jdbc; this.audit = audit;
    }

    @GetMapping
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("""
            SELECT u.id,u.username,u.display_name AS "displayName",
                   u.department_code AS "departmentCode",u.enabled,
                   u.mfa_enabled AS "mfaEnabled",
                   COALESCE((SELECT string_agg(r.code, ',') FROM user_roles ur
                     JOIN app_roles r ON r.id=ur.role_id WHERE ur.user_id=u.id),'') AS roles
            FROM app_users u ORDER BY u.created_at DESC
            """);
    }

    @PostMapping("/{username}/disable")
    @Transactional
    public Map<String,Object> disable(@PathVariable String username, Authentication actor) {
        if (username.equalsIgnoreCase(actor.getName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own active administrator account");
        Map<String,Object> target = target(username);
        if (Boolean.TRUE.equals(target.get("enabled")) && isAdmin(username)) {
            Integer activeAdmins = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_users u JOIN user_roles ur ON ur.user_id=u.id
                JOIN app_roles r ON r.id=ur.role_id WHERE r.code='SECURITY_ADMIN' AND u.enabled=TRUE
                """, Integer.class);
            if (activeAdmins != null && activeAdmins <= 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active security administrator cannot be disabled");
        }
        jdbc.update("UPDATE app_users SET enabled=FALSE WHERE username=?", username);
        audit.record(actor.getName(), "ACCOUNT_DISABLED", Map.of("username", username));
        return Map.of("username", username, "enabled", false,
            "effect", "Existing sessions are invalidated on their next protected request.");
    }

    @PostMapping("/{username}/enable")
    @Transactional
    public Map<String,Object> enable(@PathVariable String username, Authentication actor) {
        target(username);
        jdbc.update("UPDATE app_users SET enabled=TRUE WHERE username=?", username);
        audit.record(actor.getName(), "ACCOUNT_ENABLED", Map.of("username", username));
        return Map.of("username", username, "enabled", true);
    }

    private Map<String,Object> target(String username) {
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT username,enabled FROM app_users WHERE username=?", username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return rows.get(0);
    }

    private boolean isAdmin(String username) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            JOIN app_users u ON u.id=ur.user_id WHERE u.username=? AND r.code='SECURITY_ADMIN'
            """, Integer.class, username);
        return count != null && count > 0;
    }
}
