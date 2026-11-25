package io.trustlens.auth;

import io.trustlens.audit.IdentityAuditService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleAdminController {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,49}");
    private static final Map<String,String> AUTHORITIES = Map.of(
        "PERM_USER_MANAGE", "Review faculty requests and manage user accounts",
        "PERM_ROLE_MANAGE", "Create roles and assign platform authorities to roles",
        "PERM_POLICY_MANAGE", "Simulate, review and publish access policies",
        "PERM_AUDIT_READ", "Read the platform-wide security audit history"
    );

    private final JdbcTemplate jdbc;
    private final IdentityAuditService audit;

    public RoleAdminController(JdbcTemplate jdbc, IdentityAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/authorities")
    public List<Map<String,Object>> authorities() {
        return AUTHORITIES.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> Map.<String,Object>of("code",e.getKey(),"description",e.getValue())).toList();
    }

    @GetMapping
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("""
            SELECT r.id,r.code,r.description,r.is_system AS "systemManaged",
                   COALESCE(array_agg(ra.authority_code) FILTER (WHERE ra.authority_code IS NOT NULL),
                            ARRAY[]::varchar[]) AS authorities,
                   (SELECT COUNT(*) FROM user_roles ur WHERE ur.role_id=r.id) AS "assignedUsers"
            FROM app_roles r LEFT JOIN role_authorities ra ON ra.role_id=r.id
            GROUP BY r.id,r.code,r.description,r.is_system
            ORDER BY r.is_system DESC,r.code
            """);
    }

    public record RoleRequest(String code,String description,Set<String> authorities) {}

    @PostMapping
    @Transactional
    public Map<String,Object> create(@RequestBody RoleRequest request, Authentication actor) {
        if(request==null||request.code()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role code is required");
        String code=request.code().trim().toUpperCase();
        if(!CODE.matcher(code).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role code must start with a letter and contain 2-50 uppercase letters, digits or underscores");
        if(request.description()==null||request.description().isBlank()||request.description().length()>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role description is required and limited to 200 characters");
        validateAuthorities(request.authorities());
        ensureAuthorityGrantAllowed(actor,request.authorities());
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_roles WHERE code=?)",Boolean.class,code)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Role code already exists");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO app_roles(id,code,description,is_system) VALUES (?,?,?,FALSE)",id,code,request.description().trim());
        replaceAuthorities(id,request.authorities());
        audit.record(actor.getName(),"ROLE_CREATED",Map.of("role",code,"authorities",safe(request.authorities())));
        return Map.of("id",id,"code",code,"description",request.description().trim(),"authorities",safe(request.authorities()));
    }

    @PutMapping("/{code}")
    @Transactional
    public Map<String,Object> update(@PathVariable String code,@RequestBody RoleRequest request,Authentication actor) {
        String normalized=code.toUpperCase();
        UUID id=roleId(normalized);
        boolean system=Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_system FROM app_roles WHERE id=?",Boolean.class,id));
        if(system) throw new ResponseStatusException(HttpStatus.CONFLICT,"Built-in roles cannot be modified; create a custom role instead");
        if(request==null||request.description()==null||request.description().isBlank()||request.description().length()>200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role description is required and limited to 200 characters");
        validateAuthorities(request.authorities());
        ensureAuthorityGrantAllowed(actor,request.authorities());
        jdbc.update("UPDATE app_roles SET description=? WHERE id=?",request.description().trim(),id);
        replaceAuthorities(id,request.authorities());
        audit.record(actor.getName(),"ROLE_UPDATED",Map.of("role",normalized,"authorities",safe(request.authorities())));
        return Map.of("code",normalized,"description",request.description().trim(),"authorities",safe(request.authorities()),"updated",true);
    }

    @DeleteMapping("/{code}")
    @Transactional
    public Map<String,Object> delete(@PathVariable String code,Authentication actor) {
        String normalized=code.toUpperCase();
        UUID id=roleId(normalized);
        boolean system=Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_system FROM app_roles WHERE id=?",Boolean.class,id));
        if(system) throw new ResponseStatusException(HttpStatus.CONFLICT,"Built-in roles cannot be deleted");
        Integer assigned=jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE role_id=?",Integer.class,id);
        if(assigned!=null&&assigned>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove this role from all users before deleting it");
        jdbc.update("DELETE FROM app_roles WHERE id=?",id);
        audit.record(actor.getName(),"ROLE_DELETED",Map.of("role",normalized));
        return Map.of("code",normalized,"deleted",true);
    }

    private UUID roleId(String code) {
        UUID id=jdbc.query("SELECT id FROM app_roles WHERE code=?",rs->rs.next()?rs.getObject(1,UUID.class):null,code);
        if(id==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role not found");
        return id;
    }
    private void ensureAuthorityGrantAllowed(Authentication actor,Set<String> values) {
        boolean superAdmin=actor.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_SECURITY_ADMIN"));
        if(!superAdmin&&values!=null&&!values.isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only a SECURITY_ADMIN can grant platform authorities to a role");
    }
    private void validateAuthorities(Set<String> values) {
        Set<String> requested=values==null?Set.of():values;
        if(!AUTHORITIES.keySet().containsAll(requested))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more authority codes are not supported");
    }
    private void replaceAuthorities(UUID roleId,Set<String> values) {
        jdbc.update("DELETE FROM role_authorities WHERE role_id=?",roleId);
        if(values!=null) for(String value:values)
            jdbc.update("INSERT INTO role_authorities(role_id,authority_code) VALUES (?,?)",roleId,value);
    }
    private List<String> safe(Set<String> values) {
        return values==null?List.of():values.stream().sorted().toList();
    }
}
