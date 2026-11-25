package io.trustlens.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trustlens.authorization.AbacPolicyEngine;
import io.trustlens.authorization.PolicyLifecycleService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PolicyLifecycleService policies;
    private final AbacPolicyEngine engine;

    public DecisionController(JdbcTemplate jdbc,ObjectMapper mapper,PolicyLifecycleService policies,AbacPolicyEngine engine){
        this.jdbc=jdbc;this.mapper=mapper;this.policies=policies;this.engine=engine;
    }

    @GetMapping
    public List<Map<String,Object>> list(Authentication auth) {
        return jdbc.queryForList("""
            SELECT d.id,d.action,d.outcome,d.reason_code AS "reasonCode",
                   d.explanation,d.decided_at AS "decidedAt",doc.title AS document,u.display_name AS actor
            FROM access_decisions d LEFT JOIN documents doc ON doc.id=d.document_id
            LEFT JOIN app_users u ON u.id=d.actor_user_id
            WHERE u.username=? OR EXISTS(SELECT 1 FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
              JOIN app_users a ON a.id=ur.user_id WHERE a.username=? AND r.code='SECURITY_ADMIN')
            ORDER BY d.decided_at DESC LIMIT 200
            """,auth.getName(),auth.getName());
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable UUID id,Authentication auth) {
        requireDecisionAccess(id,auth.getName());
        return jdbc.queryForMap("""
            SELECT d.id,d.action,d.outcome,d.reason_code AS "reasonCode",d.explanation,
                   d.decided_at AS "decidedAt",d.evidence_snapshot AS evidence,
                   d.policy_version_id AS "policyVersionId",doc.title AS document
            FROM access_decisions d LEFT JOIN documents doc ON doc.id=d.document_id WHERE d.id=?
            """,id);
    }

    @PostMapping("/{id}/replay")
    public Map<String,Object> replay(@PathVariable UUID id,Authentication auth) {
        requireDecisionAccess(id,auth.getName());
        Map<String,Object> row=jdbc.queryForMap("""
            SELECT action,outcome,policy_version_id,evidence_snapshot::text AS evidence
            FROM access_decisions WHERE id=?
            """,id);
        try{
            JsonNode e=mapper.readTree((String)row.get("evidence"));
            HashSet<String> roles=new HashSet<>();e.path("actorRoles").forEach(v->roles.add(v.asText()));
            HashSet<String> tags=new HashSet<>();e.path("documentTags").forEach(v->tags.add(v.asText()));
            var context=new AbacPolicyEngine.Context(e.path("accountEnabled").asBoolean(),
                e.path("documentActive").asBoolean(),e.path("owner").asBoolean(),
                (String)row.get("action"),e.path("actorDepartment").asText(""),
                e.path("documentDepartment").asText(""),e.path("classification").asText("INTERNAL"),
                e.path("actorClearance").asInt(),e.path("requiredClearance").asInt(),roles,tags,
                e.path("activeGrant").asBoolean());
            UUID version=(UUID)row.get("policy_version_id");
            var replay=engine.evaluate(policies.rulesForVersion(version),context);
            String original=(String)row.get("outcome");
            return Map.of("decisionId",id,"recordedOutcome",original,
                "replayedOutcome",replay.allowed()?"ALLOW":"DENY",
                "matchesRecorded",original.equals(replay.allowed()?"ALLOW":"DENY"),
                "policyVersionId",version==null?"BASELINE":version.toString(),
                "reasonCode",replay.reasonCode(),"explanation",replay.explanation());
        }catch(Exception ex){throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Decision evidence cannot be replayed");}
    }

    private void requireDecisionAccess(UUID id,String username) {
        Boolean allowed=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM access_decisions d JOIN app_users u ON u.id=d.actor_user_id
              WHERE d.id=? AND (u.username=? OR EXISTS(SELECT 1 FROM user_roles ur
                JOIN app_roles r ON r.id=ur.role_id JOIN app_users a ON a.id=ur.user_id
                WHERE a.username=? AND r.code='SECURITY_ADMIN')))
            """,Boolean.class,id,username,username);
        if(!Boolean.TRUE.equals(allowed))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Decision not found");
    }
}
