package io.trustlens.authorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import io.trustlens.audit.IdentityAuditService;

@Service
public class PolicyLifecycleService {
    private static final String KEY = "CAMPUS_DOCUMENT_ACCESS";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IdentityAuditService audit;

    public PolicyLifecycleService(JdbcTemplate jdbc,ObjectMapper mapper,IdentityAuditService audit){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;}

    public record DraftRequest(JsonNode rules,String note) {}
    public record ReviewRequest(String note) {}

    public JsonNode activeRules() {
        List<String> rows=jdbc.query("SELECT rules::text FROM policy_versions WHERE policy_key=? AND status='PUBLISHED' ORDER BY version_number DESC LIMIT 1",
            (rs,n)->rs.getString(1),KEY);
        if(rows.isEmpty()) return baseline();
        try{return mapper.readTree(rows.get(0));}
        catch(Exception ex){throw new IllegalStateException("Published policy JSON is invalid",ex);}
    }

    public UUID activeVersionId() {
        return jdbc.query("SELECT id FROM policy_versions WHERE policy_key=? AND status='PUBLISHED' ORDER BY version_number DESC LIMIT 1",
            rs->rs.next()?rs.getObject(1,UUID.class):null,KEY);
    }

    public List<Map<String,Object>> list() {
        return jdbc.queryForList("""
            SELECT p.id,p.policy_key AS "policyKey",p.version_number AS version,p.status,
                   p.created_at AS "createdAt",p.published_at AS "publishedAt",
                   creator.username AS "createdBy",approver.username AS "approvedBy",p.review_note AS "reviewNote"
            FROM policy_versions p LEFT JOIN app_users creator ON creator.id=p.created_by
            LEFT JOIN app_users approver ON approver.id=p.approved_by
            WHERE p.policy_key=? ORDER BY p.version_number DESC
            """,KEY);
    }

    public Map<String,Object> get(UUID id) {
        return jdbc.queryForMap("""
            SELECT id,policy_key AS "policyKey",version_number AS version,status,rules,
                   created_at AS "createdAt",published_at AS "publishedAt",review_note AS "reviewNote"
            FROM policy_versions WHERE id=? AND policy_key=?
            """,id,KEY);
    }

    @Transactional
    public Map<String,Object> createDraft(DraftRequest request,String actor) {
        if(request==null||request.rules()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Policy rules are required");
        validate(request.rules());
        UUID actorId=userId(actor);
        jdbc.execute("SELECT pg_advisory_xact_lock(884423)");
        Integer next=jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM policy_versions WHERE policy_key=?",Integer.class,KEY);
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO policy_versions(id,policy_key,version_number,rules,status,created_by,published_by,published_at)
            VALUES (?,?,?,CAST(? AS jsonb),'DRAFT',?,NULL,NULL)
            """,id,KEY,next,request.rules().toString(),actorId);
        event(id,actorId,"DRAFT_CREATED",request.note());
        return Map.of("id",id,"version",next,"status","DRAFT");
    }

    @Transactional
    public Map<String,Object> submit(UUID id,String actor) {
        UUID actorId=userId(actor);
        int n=jdbc.update("UPDATE policy_versions SET status='PENDING_APPROVAL' WHERE id=? AND policy_key=? AND status='DRAFT' AND created_by=?",
            id,KEY,actorId);
        if(n!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only the draft author can submit a DRAFT policy");
        event(id,actorId,"SUBMITTED","Submitted for independent approval");
        return Map.of("id",id,"status","PENDING_APPROVAL");
    }

    @Transactional
    public Map<String,Object> approve(UUID id,ReviewRequest review,String actor) {
        UUID actorId=userId(actor);
        Map<String,Object> p=policyRow(id);
        UUID author=(UUID)p.get("created_by");
        if(actorId.equals(author)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Policy author cannot approve their own change");
        int n=jdbc.update("""
            UPDATE policy_versions SET status='PUBLISHED',approved_by=?,published_by=?,published_at=now(),review_note=?
            WHERE id=? AND status='PENDING_APPROVAL'
            """,actorId,actorId,review==null?null:review.note(),id);
        if(n!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Policy is not pending approval");
        event(id,actorId,"APPROVED",review==null?null:review.note());
        event(id,actorId,"PUBLISHED","Published immutable policy version");
        return Map.of("id",id,"status","PUBLISHED","version",p.get("version_number"));
    }

    @Transactional
    public Map<String,Object> reject(UUID id,ReviewRequest review,String actor) {
        UUID actorId=userId(actor);
        Map<String,Object> p=policyRow(id);
        if(actorId.equals(p.get("created_by"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Policy author cannot reject their own change");
        int n=jdbc.update("UPDATE policy_versions SET status='REJECTED',approved_by=?,review_note=? WHERE id=? AND status='PENDING_APPROVAL'",
            actorId,review==null?null:review.note(),id);
        if(n!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Policy is not pending approval");
        event(id,actorId,"REJECTED",review==null?null:review.note());
        return Map.of("id",id,"status","REJECTED");
    }

    public Map<String,Object> simulate(JsonNode rules,JsonNode scenarios,AbacPolicyEngine engine) {
        validate(rules);
        if(scenarios==null||!scenarios.isArray()||scenarios.size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide between 1 and 100 simulation scenarios");
        java.util.List<Map<String,Object>> results=new java.util.ArrayList<>();
        for(JsonNode s:scenarios){
            java.util.Set<String> roles=new java.util.HashSet<>();s.path("roles").forEach(v->roles.add(v.asText()));
            java.util.Set<String> tags=new java.util.HashSet<>();s.path("documentTags").forEach(v->tags.add(v.asText()));
            var context=new AbacPolicyEngine.Context(s.path("accountEnabled").asBoolean(true),
                s.path("documentActive").asBoolean(true),s.path("owner").asBoolean(false),
                s.path("action").asText("READ"),s.path("actorDepartment").asText(""),
                s.path("documentDepartment").asText(""),s.path("classification").asText("INTERNAL"),
                s.path("actorClearance").asInt(0),s.path("requiredClearance").asInt(0),roles,tags,
                s.path("activeGrant").asBoolean(false));
            var result=engine.evaluate(rules,context);
            results.add(Map.of("id",s.path("id").asText("scenario"),"allowed",result.allowed(),
                "reasonCode",result.reasonCode(),"explanation",result.explanation()));
        }
        return Map.of("results",results,"scenarioCount",results.size());
    }

    public Map<String,Object> impact(UUID policyVersionId) {
        Map<String,Object> version=policyRow(policyVersionId);
        String raw=jdbc.queryForObject("SELECT rules::text FROM policy_versions WHERE id=?",String.class,policyVersionId);
        JsonNode proposed;
        try{proposed=mapper.readTree(raw);}catch(Exception ex){throw new IllegalStateException(ex);}
        List<Map<String,Object>> history=jdbc.queryForList("""
            SELECT id,outcome,action,evidence_snapshot::text AS evidence
            FROM access_decisions WHERE evidence_snapshot IS NOT NULL
            ORDER BY decided_at DESC LIMIT 100
            """);
        java.util.List<Map<String,Object>> comparisons=new java.util.ArrayList<>();
        int gains=0,losses=0,unchanged=0;
        for(Map<String,Object> row:history){
            try{
                JsonNode e=mapper.readTree((String)row.get("evidence"));
                java.util.Set<String> roles=new java.util.HashSet<>();e.path("actorRoles").forEach(v->roles.add(v.asText()));
                java.util.Set<String> tags=new java.util.HashSet<>();e.path("documentTags").forEach(v->tags.add(v.asText()));
                var ctx=new AbacPolicyEngine.Context(e.path("accountEnabled").asBoolean(),
                    e.path("documentActive").asBoolean(),e.path("owner").asBoolean(),
                    (String)row.get("action"),e.path("actorDepartment").asText(""),
                    e.path("documentDepartment").asText(""),e.path("classification").asText("INTERNAL"),
                    e.path("actorClearance").asInt(),e.path("requiredClearance").asInt(),roles,tags,
                    e.path("activeGrant").asBoolean());
                var next=new AbacPolicyEngine().evaluate(proposed,ctx);
                boolean previous="ALLOW".equals(row.get("outcome"));
                if(!previous&&next.allowed())gains++;else if(previous&&!next.allowed())losses++;else unchanged++;
                comparisons.add(Map.of("decisionId",row.get("id").toString(),"previousOutcome",row.get("outcome"),
                    "proposedOutcome",next.allowed()?"ALLOW":"DENY","changed",previous!=next.allowed(),
                    "reasonCode",next.reasonCode()));
            }catch(Exception ex){comparisons.add(Map.of("decisionId",row.get("id").toString(),"error","Evidence could not be replayed"));}
        }
        return Map.of("policyVersionId",policyVersionId,"sampleSize",comparisons.size(),
            "gainedAccess",gains,"lostAccess",losses,"unchanged",unchanged,"comparisons",comparisons);
    }

    public JsonNode rulesForVersion(UUID versionId) {
        if(versionId==null)return baseline();
        String raw=jdbc.queryForObject("SELECT rules::text FROM policy_versions WHERE id=?",String.class,versionId);
        try{return mapper.readTree(raw);}catch(Exception ex){throw new IllegalStateException(ex);}
    }

    private void validate(JsonNode rules) {
        if(!rules.isObject()||!rules.path("rules").isArray()||rules.path("rules").isEmpty()||rules.path("rules").size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Policy must contain 1–100 rules in a rules array");
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(JsonNode r:rules.path("rules")){
            String id=r.path("id").asText("");
            String effect=r.path("effect").asText("");
            if(id.isBlank()||!ids.add(id)||!List.of("ALLOW","DENY").contains(effect.toUpperCase()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Every rule needs a unique id and ALLOW or DENY effect");
            if(!r.path("actions").isArray()||r.path("actions").isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Every rule must declare at least one action");
            for(JsonNode a:r.path("actions")) if(!List.of("READ","DOWNLOAD","*").contains(a.asText().toUpperCase()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Supported actions are READ, DOWNLOAD and *");
            for(String section:List.of("subject","resource","conditions")) if(r.has(section)&&!r.get(section).isObject())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rule "+section+" must be an object");
        }
    }

    private JsonNode baseline() {
        try{return mapper.readTree("""
          {"rules":[
            {"id":"owner","effect":"ALLOW","priority":1000,"actions":["READ","DOWNLOAD"],"conditions":{"owner":true},"reasonCode":"DOCUMENT_OWNER","description":"The authenticated owner may access their active document."},
            {"id":"public-read","effect":"ALLOW","priority":900,"actions":["READ"],"resource":{"classifications":["PUBLIC"]},"reasonCode":"PUBLIC_READ","description":"Public classification permits reading, not downloading."},
            {"id":"active-grant","effect":"ALLOW","priority":800,"actions":["READ","DOWNLOAD"],"conditions":{"grantRequired":true},"reasonCode":"ACTIVE_GRANT","description":"A current, unrevoked and unexpired grant permits this action."},
            {"id":"same-department-admin","effect":"ALLOW","priority":700,"actions":["READ"],"subject":{"roles":["SECURITY_ADMIN"],"sameDepartment":true},"reasonCode":"SECURITY_ADMIN_SAME_DEPARTMENT","description":"Security administrators may read documents in their department."}
          ]}
          """);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private UUID userId(String username){UUID id=jdbc.query("SELECT id FROM app_users WHERE username=? AND enabled=TRUE",rs->rs.next()?rs.getObject(1,UUID.class):null,username);
        if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return id;}
    private Map<String,Object> policyRow(UUID id){List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,created_by,version_number,status FROM policy_versions WHERE id=? AND policy_key=?",id,KEY);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Policy version not found");return rows.get(0);}
    private void event(UUID policyId,UUID actorId,String type,String note){
        jdbc.update("INSERT INTO policy_change_events(id,policy_version_id,actor_user_id,event_type,note) VALUES (?,?,?,?,?)",
            UUID.randomUUID(),policyId,actorId,type,note);
        String actor=jdbc.queryForObject("SELECT username FROM app_users WHERE id=?",String.class,actorId);
        audit.record(actor,"POLICY_"+type,Map.of("policyVersionId",policyId.toString(),"note",note==null?"":note));
    }
}
