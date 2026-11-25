package io.trustlens.auth;

import io.trustlens.audit.IdentityAuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AccountAdminController {
    private final JdbcTemplate jdbc;
    private final IdentityAuditService audit;

    public AccountAdminController(JdbcTemplate jdbc, IdentityAuditService audit) {
        this.jdbc = jdbc; this.audit = audit;
    }

    @GetMapping
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("""
            SELECT u.id,u.username,u.display_name AS "displayName",
                   u.department_code AS "departmentCode",u.enabled,
                   u.mfa_enabled AS "mfaEnabled",u.clearance_level AS "clearanceLevel",u.institutional_email AS "institutionalEmail",u.attributes::text AS attributes,
                   COALESCE((SELECT string_agg(r.code, ',') FROM user_roles ur
                     JOIN app_roles r ON r.id=ur.role_id WHERE ur.user_id=u.id),'') AS roles
            FROM app_users u ORDER BY u.created_at DESC
            """);
    }

    @PostMapping("/{username}/disable")
    @Transactional
    public Map<String,Object> disable(@PathVariable String username, Authentication actor) {
        if (username.equalsIgnoreCase(actor.getName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot disable your own active administrator account");
        Map<String,Object> target = target(username);
        if (Boolean.TRUE.equals(target.get("enabled")) && isAdmin(username)) {
            Integer activeAdmins = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_users u JOIN user_roles ur ON ur.user_id=u.id
                JOIN app_roles r ON r.id=ur.role_id WHERE r.code='SECURITY_ADMIN' AND u.enabled=TRUE
                """, Integer.class);
            if (activeAdmins != null && activeAdmins <= 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active security administrator cannot be disabled");
        }
        jdbc.update("UPDATE app_users SET enabled=FALSE WHERE username=?", username);
        audit.record(actor.getName(), "ACCOUNT_DISABLED", Map.of("username", username));
        return Map.of("username", username, "enabled", false,
            "effect", "Existing sessions are invalidated on their next protected request.");
    }

    @PostMapping("/{username}/enable")
    @Transactional
    public Map<String,Object> enable(@PathVariable String username, Authentication actor) {
        target(username);
        jdbc.update("UPDATE app_users SET enabled=TRUE WHERE username=?", username);
        audit.record(actor.getName(), "ACCOUNT_ENABLED", Map.of("username", username));
        return Map.of("username", username, "enabled", true);
    }

    public record AccessProfileRequest(String departmentCode,Integer clearanceLevel,Map<String,Object> attributes) {}

    @PatchMapping("/{username}/access-profile")
    @Transactional
    public Map<String,Object> updateProfile(@PathVariable String username,@RequestBody AccessProfileRequest request,Authentication actor) {
        target(username);
        if(request.clearanceLevel()==null||request.clearanceLevel()<0||request.clearanceLevel()>5)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Clearance level must be between 0 and 5");
        if(request.departmentCode()==null||!request.departmentCode().matches("[A-Za-z0-9_-]{2,40}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid department code is required");
        String attributes;
        try { attributes=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            request.attributes()==null?Map.of():request.attributes()); }
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid attributes");}
        jdbc.update("UPDATE app_users SET department_code=?,clearance_level=?,attributes=CAST(? AS jsonb) WHERE username=?",
            request.departmentCode().toUpperCase(),request.clearanceLevel(),attributes,username);
        audit.record(actor.getName(),"ACCOUNT_ACCESS_PROFILE_UPDATED",Map.of("username",username,
            "departmentCode",request.departmentCode().toUpperCase(),"clearanceLevel",request.clearanceLevel()));
        return Map.of("username",username,"departmentCode",request.departmentCode().toUpperCase(),
            "clearanceLevel",request.clearanceLevel(),"updated",true);
    }

    @PostMapping("/{username}/roles/{roleCode}")
    @Transactional
    public Map<String,Object> addRole(@PathVariable String username,@PathVariable String roleCode,Authentication actor) {
        Map<String,Object> target=target(username);
        String role=roleCode.toUpperCase();
        if(!role.matches("[A-Z][A-Z0-9_]{1,49}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role code");
        Integer roleId=jdbc.queryForObject("SELECT COUNT(*) FROM app_roles WHERE code=?",Integer.class,role);
        if(roleId==null||roleId==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role does not exist; create it in Role management first");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT id,(SELECT id FROM app_roles WHERE code=?) FROM app_users WHERE username=? ON CONFLICT DO NOTHING",role,username);
        audit.record(actor.getName(),"ROLE_ASSIGNED",Map.of("username",username,"role",role));
        return Map.of("username",username,"role",role,"assigned",true);
    }

    @DeleteMapping("/{username}/roles/{roleCode}")
    @Transactional
    public Map<String,Object> removeRole(@PathVariable String username,@PathVariable String roleCode,Authentication actor) {
        target(username);
        String role=roleCode.toUpperCase();
        if("SECURITY_ADMIN".equals(role)&&isLastAdmin(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot remove the last active SECURITY_ADMIN role");
        int changed=jdbc.update("DELETE FROM user_roles WHERE user_id=(SELECT id FROM app_users WHERE username=?) AND role_id=(SELECT id FROM app_roles WHERE code=?)",username,role);
        if(changed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role assignment not found");
        audit.record(actor.getName(),"ROLE_REMOVED",Map.of("username",username,"role",role));
        return Map.of("username",username,"role",role,"removed",true);
    }

    private boolean isLastAdmin(String username) {
        Integer n=jdbc.queryForObject("""
            SELECT COUNT(*) FROM app_users u JOIN user_roles ur ON ur.user_id=u.id
            JOIN app_roles r ON r.id=ur.role_id WHERE u.enabled=TRUE AND r.code='SECURITY_ADMIN'
            """,Integer.class);
        Integer thisAdmin=jdbc.queryForObject("SELECT COUNT(*) FROM app_users u JOIN user_roles ur ON ur.user_id=u.id JOIN app_roles r ON r.id=ur.role_id WHERE u.username=? AND u.enabled=TRUE AND r.code='SECURITY_ADMIN'",Integer.class,username);
        return n!=null&&n<=1&&thisAdmin!=null&&thisAdmin>0;
    }

    private Map<String,Object> target(String username) {
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT username,enabled FROM app_users WHERE username=?", username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return rows.get(0);
    }

    private boolean isAdmin(String username) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM user_roles ur JOIN app_roles r ON r.id=ur.role_id
            JOIN app_users u ON u.id=ur.user_id WHERE u.username=? AND r.code='SECURITY_ADMIN'
            """, Integer.class, username);
        return count != null && count > 0;
    }
}
