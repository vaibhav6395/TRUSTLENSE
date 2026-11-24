package io.trustlens.decision;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {
    private final JdbcTemplate jdbc;
    public DecisionController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        return jdbc.queryForList("""
            SELECT d.id, d.action, d.outcome, d.reason_code AS "reasonCode",
                   d.explanation, d.decided_at AS "decidedAt",
                   doc.title AS document, u.display_name AS actor
            FROM access_decisions d
            LEFT JOIN documents doc ON doc.id=d.document_id
            LEFT JOIN app_users u ON u.id=d.actor_user_id
            WHERE u.username=?
            ORDER BY d.decided_at DESC LIMIT 100
            """, auth.getName());
    }
}
