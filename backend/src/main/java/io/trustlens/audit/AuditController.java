package io.trustlens.audit;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {
    private final JdbcTemplate jdbc;
    public AuditController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        boolean fullAudit = auth.getAuthorities().stream().anyMatch(a ->
            a.getAuthority().equals("ROLE_SECURITY_ADMIN") || a.getAuthority().equals("PERM_AUDIT_READ"));
        return jdbc.queryForList("""
            SELECT e.id, e.sequence_no AS "sequence", e.event_type AS "eventType",
                   e.payload, e.previous_hash AS "previousHash", e.event_hash AS "eventHash",
                   e.occurred_at AS "occurredAt", u.username AS actor
            FROM audit_events e LEFT JOIN app_users u ON u.id=e.actor_user_id
            WHERE ? OR u.username=?
            ORDER BY e.sequence_no DESC LIMIT 100
            """, fullAudit, auth.getName());
    }
}
