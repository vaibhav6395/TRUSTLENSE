package io.trustlens.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trustlens.authorization.AbacPolicyEngine;
import io.trustlens.authorization.PolicyLifecycleService;
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
    private final AbacPolicyEngine policyEngine;
    private final PolicyLifecycleService policies;

    public DocumentService(JdbcTemplate jdbc, ObjectMapper mapper, DocumentFileStorage storage,
                           AbacPolicyEngine policyEngine, PolicyLifecycleService policies) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.storage = storage;
        this.policyEngine = policyEngine;
        this.policies = policies;
    }

    public java.util.List<Map<String, Object>> listVisible(String username) {
        Map<String,Object> actor=jdbc.queryForMap("SELECT id,department_code,enabled,clearance_level FROM app_users WHERE username=?",username);
        UUID actorId=(UUID)actor.get("id");
        java.util.Set<String> roles=new java.util.HashSet<>(jdbc.queryForList(
            "SELECT r.code FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id WHERE ur.user_id=?",String.class,actorId));
        var policy=policies.activeRules();
        java.util.List<Map<String,Object>> visible=new java.util.ArrayList<>();
        for(Map<String,Object> doc:jdbc.queryForList("""
            SELECT d.id,d.title,d.department_code AS department,d.classification,d.status,d.created_at,
                   d.description,d.tags,d.required_clearance,d.retention_until,u.display_name AS owner,u.username AS ownerUsername
            FROM documents d JOIN app_users u ON u.id=d.owner_id
            WHERE d.status='ACTIVE' ORDER BY d.created_at DESC
            """)) {
            UUID id=(UUID)doc.get("id");
            boolean grant=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                 AND permission='READ' AND starts_at<=now() AND revoked_at IS NULL
                 AND (expires_at IS NULL OR expires_at>now()))
                """,Boolean.class,id,actorId));
            java.util.Set<String> tags=new java.util.HashSet<>();
            Object raw=doc.get("tags");
            if(raw instanceof String[] arr) java.util.Collections.addAll(tags,arr);
            int clearance=((Number)actor.get("clearance_level")).intValue();
            int required=((Number)doc.get("required_clearance")).intValue();
            Object retention=doc.get("retention_until");
            boolean retentionValid=true;
            if(retention instanceof java.time.OffsetDateTime odt) retentionValid=odt.toInstant().isAfter(java.time.Instant.now());
            else if(retention instanceof java.sql.Timestamp ts) retentionValid=ts.toInstant().isAfter(java.time.Instant.now());
            boolean active="ACTIVE".equals(doc.get("status"))&&retentionValid;
            var decision=policyEngine.evaluate(policy,new AbacPolicyEngine.Context(
                Boolean.TRUE.equals(actor.get("enabled")),active,actorId.equals(
                    jdbc.queryForObject("SELECT owner_id FROM documents WHERE id=?",UUID.class,id)),
                "READ",(String)actor.get("department_code"),(String)doc.get("department"),
                (String)doc.get("classification"),clearance,required,roles,tags,grant));
            if(decision.allowed()) visible.add(doc);
        }
        return visible;
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
        jdbc.update("""
            UPDATE access_grants SET download_count=download_count+1
            WHERE document_id=? AND subject_user_id=(SELECT id FROM app_users WHERE username=?)
              AND permission='DOWNLOAD' AND starts_at<=now() AND revoked_at IS NULL
              AND (expires_at IS NULL OR expires_at>now())
              AND (max_downloads IS NULL OR download_count<max_downloads)
            """,documentId,username);
        jdbc.update("""
            INSERT INTO document_download_events(id,document_id,user_id,grant_id)
            VALUES (?, ?, (SELECT id FROM app_users WHERE username=?),
              (SELECT id FROM access_grants WHERE document_id=? AND subject_user_id=(SELECT id FROM app_users WHERE username=?)
               AND permission='DOWNLOAD' AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1))
            """,UUID.randomUUID(),documentId,username,documentId,username);
        return new DownloadFile(path, (String) doc.get("title"), contentType, ((Number) sizeValue).longValue());
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String title, String classification, String description, java.util.List<String> tags, int requiredClearance, String username) throws IOException {
        if (title == null || title.isBlank() || title.length() > 240) {
            throw new IllegalArgumentException("Title is required and must be at most 240 characters");
        }
        if (classification == null || !CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException("Classification must be PUBLIC, INTERNAL, CONFIDENTIAL or RESTRICTED");
        }
        if(requiredClearance<0||requiredClearance>5) throw new IllegalArgumentException("Required clearance must be between 0 and 5");
        if(tags!=null&&(tags.size()>20||tags.stream().anyMatch(t->t==null||t.isBlank()||t.length()>40)))
            throw new IllegalArgumentException("Use at most 20 non-empty tags, each at most 40 characters");
        Map<String, Object> actor = jdbc.queryForMap(
            "SELECT id, department_code, clearance_level FROM app_users WHERE username=? AND enabled=TRUE", username);
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
                                      content_type,size_bytes,content_sha256,description,tags,required_clearance)
                VALUES (?,?,?,?,?,?, 'ACTIVE',?,?,?,?,?,?)
                """, documentId, title.trim(), ownerId, actor.get("department_code"), classification,
                stored.storageKey(), stored.contentType(), stored.sizeBytes(), stored.sha256(),
                description==null?"":description.trim(),tags==null?new String[]{}:tags.toArray(String[]::new),requiredClearance);
            appendAudit(ownerId, "DOCUMENT_UPLOADED", json(Map.of(
                "documentId", documentId.toString(), "classification", classification,
                "sizeBytes", stored.sizeBytes(), "sha256", stored.sha256())));
        } catch (RuntimeException ex) {
            storage.deleteQuietly(stored.storageKey());
            throw ex;
        }
        return Map.of("id", documentId, "title", title.trim(), "classification", classification,
                      "description",description==null?"":description.trim(),"tags",tags==null?java.util.List.of():tags,
                      "requiredClearance",requiredClearance,"sizeBytes", stored.sizeBytes(), "sha256", stored.sha256());
    }

    public Map<String,Object> updateMetadata(UUID documentId,String username,DocumentMetadataRequest request) {
        UUID actorId=userId(username);
        requireDocumentManager(documentId,actorId);
        if(request.title()==null||request.title().isBlank()||request.title().length()>240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Title is required and must be at most 240 characters");
        if(!CLASSIFICATIONS.contains(request.classification()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid classification");
        if(request.requiredClearance()<0||request.requiredClearance()>5)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required clearance must be between 0 and 5");
        if(request.tags()!=null&&(request.tags().size()>20||request.tags().stream().anyMatch(t->t==null||t.isBlank()||t.length()>40)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid document tags");
        jdbc.update("""
            UPDATE documents SET title=?,description=?,classification=?,tags=?,required_clearance=?,updated_at=now()
            WHERE id=? AND status='ACTIVE'
            """,request.title().trim(),request.description()==null?"":request.description().trim(),
            request.classification(),request.tags()==null?new String[]{}:request.tags().toArray(String[]::new),
            request.requiredClearance(),documentId);
        appendAudit(actorId,"DOCUMENT_METADATA_UPDATED",json(Map.of("documentId",documentId.toString(),
            "classification",request.classification(),"requiredClearance",request.requiredClearance())));
        return Map.of("id",documentId,"updated",true);
    }

    public Map<String,Object> retire(UUID documentId,String username) {
        UUID actorId=userId(username);
        requireDocumentManager(documentId,actorId);
        int changed=jdbc.update("UPDATE documents SET status='RETIRED',updated_at=now() WHERE id=? AND status='ACTIVE'",documentId);
        if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Document is already retired or unavailable");
        jdbc.update("UPDATE access_grants SET revoked_at=now(),revoked_by=? WHERE document_id=? AND revoked_at IS NULL",actorId,documentId);
        appendAudit(actorId,"DOCUMENT_RETIRED",json(Map.of("documentId",documentId.toString())));
        return Map.of("id",documentId,"status","RETIRED");
    }

    private UUID userId(String username) {
        UUID id=jdbc.query("SELECT id FROM app_users WHERE username=? AND enabled=TRUE",rs->rs.next()?rs.getObject(1,UUID.class):null,username);
        if(id==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }
    private void requireDocumentManager(UUID documentId,UUID actorId) {
        Boolean allowed=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM documents d WHERE d.id=? AND
              (d.owner_id=? OR EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
               WHERE ur.user_id=? AND r.code='SECURITY_ADMIN')))
            """,Boolean.class,documentId,actorId,actorId);
        if(!Boolean.TRUE.equals(allowed)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Document owner or SECURITY_ADMIN role required");
    }
    public record DocumentMetadataRequest(String title,String description,String classification,
        java.util.List<String> tags,int requiredClearance) {}

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> check(UUID documentId, String username, String action) {
        if (!"READ".equals(action) && !"DOWNLOAD".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be READ or DOWNLOAD");
        }
        return evaluateAndRecord(documentId, username, action);
    }

    private Map<String, Object> evaluateAndRecord(UUID documentId, String username, String action) {
        Map<String, Object> actor = jdbc.queryForMap(
            "SELECT id, department_code, enabled, clearance_level FROM app_users WHERE username=?", username);
        Map<String, Object> doc;
        try {
            doc = jdbc.queryForMap("SELECT id, owner_id, department_code, classification, status, tags, required_clearance,retention_until FROM documents WHERE id=?", documentId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }

        UUID actorId = (UUID) actor.get("id");
        UUID ownerId = (UUID) doc.get("owner_id");
        String actorDept = (String) actor.get("department_code");
        String docDept = (String) doc.get("department_code");
        String classification = (String) doc.get("classification");
        boolean enabled = Boolean.TRUE.equals(actor.get("enabled"));
        Object retention=doc.get("retention_until");
        boolean retentionValid=true;
        if(retention instanceof java.time.OffsetDateTime odt) retentionValid=odt.toInstant().isAfter(java.time.Instant.now());
        else if(retention instanceof java.sql.Timestamp ts) retentionValid=ts.toInstant().isAfter(java.time.Instant.now());
        boolean activeDocument="ACTIVE".equals(doc.get("status"))&&retentionValid;
        boolean owner = actorId.equals(ownerId);
        String permission = "DOWNLOAD".equals(action) ? "DOWNLOAD" : "READ";
        Map<String, Object> grantState = jdbc.queryForMap("""
            SELECT
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND starts_at <= now() AND revoked_at IS NULL
                     AND (expires_at IS NULL OR expires_at > now())
                     AND (max_downloads IS NULL OR download_count < max_downloads)) AS active,
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND revoked_at IS NULL AND expires_at <= now()) AS expired,
              EXISTS(SELECT 1 FROM access_grants WHERE document_id=? AND subject_user_id=?
                     AND permission=? AND revoked_at IS NOT NULL) AS revoked
            """, documentId, actorId, permission, documentId, actorId, permission,
                 documentId, actorId, permission);
        boolean activeGrant = Boolean.TRUE.equals(grantState.get("active"));
        boolean expiredGrant = Boolean.TRUE.equals(grantState.get("expired"));
        boolean revokedGrant = Boolean.TRUE.equals(grantState.get("revoked"));
        java.util.Set<String> roles = new java.util.HashSet<>(jdbc.queryForList("""
            SELECT r.code FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id WHERE ur.user_id=?
            """, String.class, actorId));
        Object rawTags = doc.get("tags");
        java.util.Set<String> tags = new java.util.HashSet<>();
        if (rawTags instanceof String[] tagArray) java.util.Collections.addAll(tags, tagArray);
        else if (rawTags instanceof java.sql.Array sqlArray) {
            try { java.util.Collections.addAll(tags,(String[])sqlArray.getArray()); }
            catch (java.sql.SQLException ignored) { }
        }
        int actorClearance = ((Number)actor.get("clearance_level")).intValue();
        int requiredClearance = ((Number)doc.get("required_clearance")).intValue();
        var policyRules = policies.activeRules();
        var policyResult = policyEngine.evaluate(policyRules,new AbacPolicyEngine.Context(
            enabled,activeDocument,owner,action,actorDept,docDept,classification,
            actorClearance,requiredClearance,roles,tags,activeGrant));
        boolean allowed = policyResult.allowed();
        String reason = policyResult.reasonCode();
        if (!allowed && expiredGrant) reason = "GRANT_EXPIRED";
        else if (!allowed && revokedGrant) reason = "GRANT_REVOKED";
        String explanation = policyResult.explanation();
        UUID decisionId = UUID.randomUUID();
        String evidence = json(Map.of(
            "actorDepartment", actorDept == null ? "" : actorDept,
            "documentDepartment", docDept == null ? "" : docDept,
            "classification", classification,
            "accountEnabled", enabled,
            "documentActive",activeDocument,
            "owner",owner,
            "actorRoles",roles,
            "actorClearance",actorClearance,
            "requiredClearance",requiredClearance,
            "documentTags",tags,
            "activeGrant",activeGrant,
            "grantExpired", expiredGrant,
            "grantRevoked", revokedGrant,
            "action", action
        ));
        UUID policyVersionId = policies.activeVersionId();
        jdbc.update("""
            INSERT INTO access_decisions(id,actor_user_id,document_id,action,outcome,reason_code,explanation,policy_version_id,evidence_snapshot)
            VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb))
            """, decisionId, actorId, documentId, action, allowed ? "ALLOW" : "DENY", reason, explanation, policyVersionId, evidence);
        appendAudit(actorId,"ACCESS_"+(allowed?"ALLOW":"DENY"),
            json(Map.of("decisionId",decisionId.toString(),"documentId",documentId.toString(),
                "action",action,"reasonCode",reason,"policyVersionId",policyVersionId==null?"BASELINE":policyVersionId.toString())));
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
