package io.trustlens.grant;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GrantService {
    private final JdbcTemplate jdbc;
    public GrantService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Map<String, Object> revoke(UUID grantId, String username) {
        UUID actorId = jdbc.query("SELECT id FROM app_users WHERE username=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, username);
        if (actorId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Boolean admin = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')
            """, Boolean.class, actorId);
        if (!Boolean.TRUE.equals(admin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Security administrator role required");
        int updated = jdbc.update("""
            UPDATE access_grants SET revoked_at=now(), revoked_by=?
            WHERE id=? AND revoked_at IS NULL
            """, actorId, grantId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active grant not found");
        jdbc.execute("SELECT pg_advisory_xact_lock(884422)");
        Long sequence = jdbc.queryForObject("SELECT nextval('audit_event_seq')", Long.class);
        String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY sequence_no DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        String payload = "{\"grantId\":\"" + grantId + "\"}";
        String hash = sha256((previous == null ? "" : previous) + "|" + sequence + "|" + actorId + "|GRANT_REVOKED|" + payload);
        jdbc.update("""
            INSERT INTO audit_events(id,sequence_no,actor_user_id,event_type,payload,previous_hash,event_hash)
            VALUES (?,?,?,'GRANT_REVOKED',CAST(? AS jsonb),?,?)
            """, UUID.randomUUID(), sequence, actorId, payload, previous, hash);
        return Map.of("grantId", grantId, "revoked", true, "revokedAt", java.time.Instant.now().toString());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
