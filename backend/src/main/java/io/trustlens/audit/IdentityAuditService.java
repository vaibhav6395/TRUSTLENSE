package io.trustlens.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdentityAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IdentityAuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper;
    }

    public void record(String actorUsername, String eventType, Map<String,Object> details) {
        UUID actorId = null;
        if (actorUsername != null) {
            actorId = jdbc.query("SELECT id FROM app_users WHERE username=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, actorUsername);
        }
        try {
            String payload = mapper.writeValueAsString(details);
            jdbc.execute("SELECT pg_advisory_xact_lock(884422)");
            Long sequence = jdbc.queryForObject("SELECT nextval('audit_event_seq')", Long.class);
            String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY sequence_no DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null);
            String hash = sha256((previous == null ? "" : previous) + "|" + sequence + "|" +
                (actorId == null ? "null" : actorId) + "|" + eventType + "|" + payload);
            jdbc.update("""
                INSERT INTO audit_events(id,sequence_no,actor_user_id,event_type,payload,previous_hash,event_hash)
                VALUES (?,?,?, ?,CAST(? AS jsonb),?,?)
                """, UUID.randomUUID(), sequence, actorId, eventType, payload, previous, hash);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize identity audit event", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
