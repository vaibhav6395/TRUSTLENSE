package io.trustlens.grant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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

    public java.util.List<Map<String, Object>> list(String username) {
        UUID actorId = actorId(username);
        requireAdmin(actorId);
        return jdbc.queryForList("""
            SELECT g.id, d.title AS document, u.username AS subject,
                   g.permission, g.expires_at AS "expiresAt", g.revoked_at AS "revokedAt"
            FROM access_grants g JOIN documents d ON d.id=g.document_id
            JOIN app_users u ON u.id=g.subject_user_id
            ORDER BY g.created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> create(CreateGrantRequest request, String username) {
        UUID actorId = actorId(username);
        requireAdmin(actorId);
        if (request == null || request.documentId() == null || request.subjectUsername() == null
                || request.subjectUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document and subject user are required");
        }
        String permission = request.permission() == null ? "" : request.permission().trim().toUpperCase();
        if (!"READ".equals(permission) && !"DOWNLOAD".equals(permission)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission must be READ or DOWNLOAD");
        }
        UUID subjectId = jdbc.query("SELECT id FROM app_users WHERE username=? AND enabled=TRUE AND enabled=TRUE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, request.subjectUsername().trim());
        if (subjectId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enabled subject user not found");
        Boolean documentExists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM documents WHERE id=? AND status='ACTIVE')",
            Boolean.class, request.documentId());
        if (!Boolean.TRUE.equals(documentExists)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active document not found");

        Timestamp expiry = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                Instant instant = OffsetDateTime.parse(request.expiresAt()).toInstant();
                if (!instant.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Grant expiry must be in the future");
                expiry = Timestamp.from(instant);
            } catch (java.time.format.DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be an ISO-8601 timestamp with timezone");
            }
        }
        UUID grantId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO access_grants(id,document_id,subject_user_id,permission,granted_by,expires_at)
            VALUES (?,?,?,?,?,?)
            """, grantId, request.documentId(), subjectId, permission, actorId, expiry);
        appendAudit(actorId, "GRANT_CREATED", "{\"grantId\":\"" + grantId + "\",\"documentId\":\"" +
            request.documentId() + "\",\"subject\":\"" + request.subjectUsername().trim() +
            "\",\"permission\":\"" + permission + "\"}");
        return Map.of("grantId", grantId, "documentId", request.documentId(),
            "subjectUsername", request.subjectUsername().trim(), "permission", permission,
            "expiresAt", expiry == null ? "" : expiry.toInstant().toString());
    }

    @Transactional
    public Map<String, Object> revoke(UUID grantId, String username) {
        UUID actorId = actorId(username);
        requireAdmin(actorId);
        int updated = jdbc.update("""
            UPDATE access_grants SET revoked_at=now(), revoked_by=?
            WHERE id=? AND revoked_at IS NULL
            """, actorId, grantId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active grant not found");
        appendAudit(actorId, "GRANT_REVOKED", "{\"grantId\":\"" + grantId + "\"}");
        return Map.of("grantId", grantId, "revoked", true, "revokedAt", Instant.now().toString());
    }

    private UUID actorId(String username) {
        UUID id = jdbc.query("SELECT id FROM app_users WHERE username=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, username);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }

    private void requireAdmin(UUID actorId) {
        Boolean admin = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')
            """, Boolean.class, actorId);
        if (!Boolean.TRUE.equals(admin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Security administrator role required");
    }

    private void appendAudit(UUID actorId, String eventType, String payload) {
        jdbc.execute("SELECT pg_advisory_xact_lock(884422)");
        Long sequence = jdbc.queryForObject("SELECT nextval('audit_event_seq')", Long.class);
        String previous = jdbc.query("SELECT event_hash FROM audit_events ORDER BY sequence_no DESC LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        String hash = sha256((previous == null ? "" : previous) + "|" + sequence + "|" + actorId + "|" + eventType + "|" + payload);
        jdbc.update("""
            INSERT INTO audit_events(id,sequence_no,actor_user_id,event_type,payload,previous_hash,event_hash)
            VALUES (?,?,?, ?,CAST(? AS jsonb),?,?)
            """, UUID.randomUUID(), sequence, actorId, eventType, payload, previous, hash);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record CreateGrantRequest(UUID documentId, String subjectUsername, String permission, String expiresAt) {}
}
