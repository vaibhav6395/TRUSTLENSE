package io.trustlens.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trustlens.authorization.PolicyEvaluator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private static final Set<String> CLASSIFICATIONS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DocumentFileStorage storage;

    public DocumentService(JdbcTemplate jdbc, ObjectMapper mapper, DocumentFileStorage storage) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.storage = storage;
    }

    public java.util.List<Map<String, Object>> listVisible(String username) {
        return jdbc.queryForList("""
            SELECT DISTINCT d.id, d.title, d.department_code AS department,
                   d.classification, d.status, d.created_at,
                   u.display_name AS owner
            FROM documents d
            JOIN app_users u ON u.id=d.owner_id
            JOIN app_users actor ON actor.username=?
            WHERE actor.enabled=TRUE AND d.status='ACTIVE'
              AND (d.owner_id=actor.id
                   OR (d.classification='PUBLIC')
                   OR EXISTS (SELECT 1 FROM access_grants g
                     WHERE g.document_id=d.id AND g.subject_user_id=actor.id
                       AND g.permission IN ('READ','DOWNLOAD')
                       AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > now()))
                   OR (actor.department_code=d.department_code AND EXISTS (
                     SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
                     WHERE ur.user_id=actor.id AND r.code='SECURITY_ADMIN')))
            ORDER BY d.created_at DESC
            """, username);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> read(UUID documentId, String username, String action) {
        Map<String, Object> decision = evaluateAndRecord(documentId, username, action);
        if (!Boolean.TRUE.equals(decision.get("allowed"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, (String) decision.get("explanation"));
        }
        Map<String, Object> doc = jdbc.queryForMap("""
            SELECT d.id, d.title, d.department_code AS department, d.classification,
                   d.status, u.display_name AS owner
            FROM documents d JOIN app_users u ON u.id=d.owner_id WHERE d.id=?
            """, documentId);
        return Map.of("document", doc, "decision", decision);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public DownloadFile download(UUID documentId, String username) {
        Map<String, Object> decision = evaluateAndRecord(documentId, username, "DOWNLOAD");
        if (!Boolean.TRUE.equals(decision.get("allowed"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, (String) decision.get("explanation"));
        }
        Map<String, Object> doc = jdbc.queryForMap("""
            SELECT title, storage_key, content_type, size_bytes
            FROM documents WHERE id=? AND status='ACTIVE'
            """, documentId);
        String key = (String) doc.get("storage_key");
        String contentType = (String) doc.get("content_type");
        Object sizeValue = doc.get("size_bytes");
        if (contentType == null || sizeValue == null || key == null || key.startsWith("demo://")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This synthetic document has no uploaded file content");
        }
        Path path;
        try { path = storage.resolve(key); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document content is unavailable");
        }
        if (!java.nio.file.Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document content is unavailable");
        }
        return new DownloadFile(path, (String) doc.get("title"), contentType, ((Number) sizeValue).longValue());
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String title, String classification, String username) throws IOException {
        if (title == null || title.isBlank() || title.length() > 240) {
            throw new IllegalArgumentException("Title is required and must be at most 240 characters");
        }
        if (classification == null || !CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException("Classification must be PUBLIC, INTERNAL, CONFIDENTIAL or RESTRICTED");
        }
        Map<String, Object> actor = jdbc.queryForMap(
            "SELECT id, department_code FROM app_users WHERE username=? AND enabled=TRUE", username);
        UUID ownerId = (UUID) actor.get("id");
        if ("PUBLIC".equals(classification) || "RESTRICTED".equals(classification)) {
            Boolean admin = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
                WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')
                """, Boolean.class, ownerId);
            if (!Boolean.TRUE.equals(admin)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only SECURITY_ADMIN may publish PUBLIC or RESTRICTED documents");
            }
        }
        DocumentFileStorage.StoredFile stored = storage.store(file);
        UUID documentId = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO documents(id,title,owner_id,department_code,classification,storage_key,status,
                                      content_type,size_bytes,content_sha256)
                VALUES (?,?,?,?,?,?, 'ACTIVE',?,?,?)
                """, documentId, title.trim(), ownerId, actor.get("department_code"), classification,
                stored.storageKey(), stored.contentType(), stored.sizeBytes(), stored.sha256());
            appendAudit(ownerId, "DOCUMENT_UPLOADED", json(Map.of(
                "documentId", documentId.toString(), "classification", classification,
                "sizeBytes", stored.sizeBytes(), "sha256", stored.sha256())));
        } catch (RuntimeException ex) {
            storage.deleteQuietly(stored.storageKey());
            throw ex;
        }
        return Map.of("id", documentId, "title", title.trim(), "classification", classification,
                      "sizeBytes", stored.sizeBytes(), "sha256", stored.sha256());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> check(UUID documentId, String username, String action) {
        if (!"READ".equals(action) && !"DOWNLOAD".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be READ or DOWNLOAD");
        }
        return evaluateAndRecord(documentId, username, action);
    }

    private Map<String, Object> evaluateAndRecord(UUID documentId, String username, String action) {
        Map<String, Object> actor = jdbc.queryForMap(
            "SELECT id, department_code, enabled FROM app_users WHERE username=?", username);
        Map<String, Object> doc;
        try {
            doc = jdbc.queryForMap("SELECT id, owner_id, department_code, classification, status FROM documents WHERE id=?", documentId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }

        UUID actorId = (UUID) actor.get("id");
        UUID ownerId = (UUID) doc.get("owner_id");
        String actorDept = (String) actor.get("department_code");
        String docDept = (String) doc.get("department_code");
        String classification = (String) doc.get("classification");
        boolean enabled = Boolean.TRUE.equals(actor.get("enabled"));
        boolean activeDocument = "ACTIVE".equals(doc.get("status"));
        boolean owner = actorId.equals(ownerId);
        boolean publicRead = "PUBLIC".equals(classification) && "READ".equals(action);
        boolean securityAdmin = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
             WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')
            """, Boolean.class, actorId));
        String permission = "DOWNLOAD".equals(action) ? "DOWNLOAD" : "READ";
        Map<String, Object> grantState = jdbc.queryForMap("""
            SELECT
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())) AS active,
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND revoked_at IS NULL AND expires_at <= now()) AS expired,
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND revoked_at IS NOT NULL) AS revoked
            """, documentId, actorId, permission, documentId, actorId, permission,
                 documentId, actorId, permission);
        boolean activeGrant = Boolean.TRUE.equals(grantState.get("active"));
        boolean expiredGrant = Boolean.TRUE.equals(grantState.get("expired"));
        boolean revokedGrant = Boolean.TRUE.equals(grantState.get("revoked"));
        boolean departmentMatch = actorDept != null && actorDept.equals(docDept);
        PolicyEvaluator.Result policyResult = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            enabled, activeDocument, owner, publicRead, activeGrant,
            securityAdmin && departmentMatch && "READ".equals(action)
        ));
        boolean allowed = policyResult.allowed();
        String reason = policyResult.reasonCode();
        if (!allowed && expiredGrant) reason = "GRANT_EXPIRED";
        else if (!allowed && revokedGrant) reason = "GRANT_REVOKED";
        String explanation = allowed ? "The current server-side policy permits this action (" + reason + ")."
            : "Access denied because no active policy condition permits this action (" + reason + ").";
        UUID decisionId = UUID.randomUUID();
        String evidence = json(Map.of(
            "actorDepartment", actorDept == null ? "" : actorDept,
            "documentDepartment", docDept == null ? "" : docDept,
            "classification", classification,
            "accountEnabled", enabled,
            "documentActive", activeDocument,
            "owner", owner,
            "activeGrant", activeGrant,
            "grantExpired", expiredGrant,
            "grantRevoked", revokedGrant,
            "action", action
        ));
        jdbc.update("""
            INSERT INTO access_decisions(id,actor_user_id,document_id,action,outcome,reason_code,explanation,policy_version_id,evidence_snapshot)
            VALUES (?,?,?,?,?,?,?,NULL,CAST(? AS jsonb))
            """, decisionId, actorId, documentId, action, allowed ? "ALLOW" : "DENY", reason, explanation, evidence);
        appendAudit(actorId, "ACCESS_" + (allowed ? "ALLOW" : "DENY"),
            json(Map.of("decisionId", decisionId.toString(), "documentId", documentId.toString(),
                        "action", action, "reasonCode", reason)));
        return Map.of("decisionId", decisionId, "allowed", allowed,
                      "outcome", allowed ? "ALLOW" : "DENY",
                      "reasonCode", reason, "explanation", explanation);
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

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize security evidence", ex); }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record DownloadFile(Path path, String title, String contentType, long sizeBytes) {}
}
