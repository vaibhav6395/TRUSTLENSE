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
        UUID actorId=actorId(username);
        boolean admin=isAdmin(actorId);
        return jdbc.queryForList("""
            SELECT g.id,d.title AS document,u.username AS subject,g.permission,
                   g.expires_at AS "expiresAt",g.revoked_at AS "revokedAt",
                   g.purpose,g.max_downloads AS "maxDownloads",g.download_count AS "downloadCount",
                   g.starts_at AS "startsAt",creator.username AS "createdBy"
            FROM access_grants g JOIN documents d ON d.id=g.document_id
            JOIN app_users u ON u.id=g.subject_user_id JOIN app_users creator ON creator.id=g.granted_by
            WHERE ? OR d.owner_id=? OR g.granted_by=?
            ORDER BY g.created_at DESC
            """,admin,actorId,actorId);
    }

    @Transactional
    public Map<String, Object> create(CreateGrantRequest request, String username) {
        UUID actorId=actorId(username);
        if (request == null || request.documentId() == null || request.subjectUsername() == null
                || request.subjectUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document and subject user are required");
        }
        String permission = request.permission() == null ? "" : request.permission().trim().toUpperCase();
        if (!"READ".equals(permission) && !"DOWNLOAD".equals(permission)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permission must be READ or DOWNLOAD");
        }
        Map<String,Object> subject=jdbc.queryForMap("SELECT id,department_code FROM app_users WHERE username=? AND enabled=TRUE",
            request.subjectUsername().trim());
        UUID subjectId=(UUID)subject.get("id");
        Map<String,Object> document=jdbc.queryForMap("SELECT owner_id,department_code FROM documents WHERE id=? AND status='ACTIVE'",
            request.documentId());
        boolean admin=isAdmin(actorId);
        boolean owner=actorId.equals(document.get("owner_id"));
        if(!admin&&!owner) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the document owner or SECURITY_ADMIN may share this document");
        if(!admin&&!java.util.Objects.equals(subject.get("department_code"),document.get("department_code")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Faculty owners may share only within their department");
        if(request.maxDownloads()!=null&&(request.maxDownloads()<1||request.maxDownloads()>10000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"maxDownloads must be between 1 and 10000");
        String purpose=request.purpose()==null?"":request.purpose().trim();
        if(purpose.length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Share purpose is limited to 500 characters");

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
            INSERT INTO access_grants(id,document_id,subject_user_id,permission,granted_by,expires_at,purpose,max_downloads)
            VALUES (?,?,?,?,?,?,?,?)
            """,grantId,request.documentId(),subjectId,permission,actorId,expiry,purpose,request.maxDownloads());
        appendAudit(actorId, "GRANT_CREATED", "{\"grantId\":\"" + grantId + "\",\"documentId\":\"" +
            request.documentId() + "\",\"subject\":\"" + request.subjectUsername().trim() +
            "\",\"permission\":\"" + permission + "\"}");
        return Map.of("grantId", grantId, "documentId", request.documentId(),
            "subjectUsername", request.subjectUsername().trim(), "permission", permission,
            "expiresAt", expiry == null ? "" : expiry.toInstant().toString());
    }

    @Transactional
    public Map<String, Object> revoke(UUID grantId, String username) {
        UUID actorId=actorId(username);
        int updated=jdbc.update("""
            UPDATE access_grants g SET revoked_at=now(),revoked_by=?
            WHERE g.id=? AND g.revoked_at IS NULL AND (
              g.granted_by=? OR EXISTS(SELECT 1 FROM documents d WHERE d.id=g.document_id AND d.owner_id=?)
              OR EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
                        WHERE ur.user_id=? AND r.code='SECURITY_ADMIN'))
            """,actorId,grantId,actorId,actorId,actorId);
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

    private boolean isAdmin(UUID actorId) {
        Boolean admin=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')
            """,Boolean.class,actorId);
        return Boolean.TRUE.equals(admin);
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

    public record CreateGrantRequest(UUID documentId,String subjectUsername,String permission,String expiresAt,String purpose,Integer maxDownloads) {}
}
