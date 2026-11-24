package io.trustlens.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trustlens.authorization.PolicyEvaluator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DocumentService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public java.util.List<Map<String, Object>> listVisible(String username) {
        return jdbc.queryForList("""
            SELECT DISTINCT d.id, d.title, d.department_code AS department,
                   d.classification, d.status, d.created_at,
                   u.display_name AS owner
            FROM documents d
            JOIN app_users u ON u.id=d.owner_id
            JOIN app_users actor ON actor.username=?
            WHERE d.status='ACTIVE'
              AND (d.classification='PUBLIC' OR d.owner_id=actor.id
                   OR EXISTS (SELECT 1 FROM access_grants g
                     WHERE g.document_id=d.id AND g.subject_user_id=actor.id AND g.permission='READ'
                       AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > now())))
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

    @Transactional
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
        Boolean grant = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM access_grants
             WHERE document_id=? AND subject_user_id=? AND permission=?
               AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now()))
            """, Boolean.class, documentId, actorId, permission);
        boolean departmentMatch = actorDept != null && actorDept.equals(docDept);
        PolicyEvaluator.Result policyResult = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            enabled, activeDocument, owner, publicRead, Boolean.TRUE.equals(grant),
            securityAdmin && departmentMatch && "READ".equals(action)
        ));
        boolean allowed = policyResult.allowed();
        String reason = policyResult.reasonCode();
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
            "activeGrant", Boolean.TRUE.equals(grant),
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
}
