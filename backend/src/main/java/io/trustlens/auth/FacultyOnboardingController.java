package io.trustlens.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class FacultyOnboardingController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public FacultyOnboardingController(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.passwords = passwords;
    }

    public record SignupRequest(
        @NotBlank @Size(min=3,max=80) String username,
        @NotBlank @Email @Size(max=254) String institutionalEmail,
        @NotBlank @Size(min=12,max=72) String password,
        @NotBlank @Size(max=160) String displayName,
        @NotBlank @Size(max=40) String departmentCode,
        @NotBlank @Size(max=80) String facultyIdentifier) {}
    public record ReviewRequest(@Size(max=1000) String note) {}

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> signup(@Valid @RequestBody SignupRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.institutionalEmail().trim().toLowerCase();
        if (username.length() < 3 || !username.matches("[a-z0-9._-]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username may contain lowercase letters, numbers, dots, underscores and hyphens");
        Integer exists = jdbc.queryForObject("""
            SELECT (SELECT COUNT(*) FROM app_users WHERE username=?) +
                   (SELECT COUNT(*) FROM faculty_signup_requests WHERE username=? OR institutional_email=?)
            """, Integer.class, username, username, email);
        if (exists != null && exists > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or institutional email is already registered");
        jdbc.update("""
            INSERT INTO faculty_signup_requests(id,username,institutional_email,password_hash,display_name,
                department_code,faculty_identifier,status)
            VALUES (?,?,?,?,?,?,?,'PENDING')
            """, UUID.randomUUID(), username, email, passwords.encode(request.password()),
            request.displayName().trim(), request.departmentCode().trim().toUpperCase(),
            request.facultyIdentifier().trim());
        return Map.of("submitted", true, "status", "PENDING",
            "message", "Your faculty request is awaiting administrator verification.");
    }

    @GetMapping("/admin/signup-requests")
    public List<Map<String,Object>> listRequests() {
        return jdbc.queryForList("""
            SELECT id,username,institutional_email AS "institutionalEmail",display_name AS "displayName",
                   department_code AS "departmentCode",faculty_identifier AS "facultyIdentifier",
                   status,review_note AS "reviewNote",created_at AS "createdAt",reviewed_at AS "reviewedAt"
            FROM faculty_signup_requests ORDER BY
              CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, created_at DESC
            """);
    }

    @PostMapping("/admin/signup-requests/{id}/approve")
    @Transactional
    public Map<String,Object> approve(@PathVariable UUID id, @RequestBody(required=false) ReviewRequest review,
                                      Authentication admin) {
        Map<String,Object> request = pendingRequest(id);
        UUID userId = UUID.randomUUID();
        String username = (String) request.get("username");
        jdbc.update("""
            INSERT INTO app_users(id,username,password_hash,display_name,department_code,enabled)
            VALUES (?,?,?,?,?,TRUE)
            """, userId, username, request.get("password_hash"), request.get("display_name"), request.get("department_code"));
        ensureRole("FACULTY", "Faculty member");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE code='FACULTY'",
            userId);
        jdbc.update("""
            UPDATE faculty_signup_requests SET status='APPROVED',review_note=?,reviewed_by=
              (SELECT id FROM app_users WHERE username=?),reviewed_at=now() WHERE id=?
            """, review == null ? null : review.note(), admin.getName(), id);
        return Map.of("approved", true, "username", username, "nextStep", "Faculty member must enroll MFA at first login.");
    }

    @PostMapping("/admin/signup-requests/{id}/reject")
    @Transactional
    public Map<String,Object> reject(@PathVariable UUID id, @RequestBody(required=false) ReviewRequest review,
                                     Authentication admin) {
        pendingRequest(id);
        jdbc.update("""
            UPDATE faculty_signup_requests SET status='REJECTED',review_note=?,reviewed_by=
              (SELECT id FROM app_users WHERE username=?),reviewed_at=now() WHERE id=?
            """, review == null ? null : review.note(), admin.getName(), id);
        return Map.of("rejected", true);
    }

    private Map<String,Object> pendingRequest(UUID id) {
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT * FROM faculty_signup_requests WHERE id=? AND status='PENDING' FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending signup request not found");
        return rows.get(0);
    }

    private void ensureRole(String code, String description) {
        jdbc.update("INSERT INTO app_roles(id,code,description) VALUES (?, ?, ?) ON CONFLICT(code) DO NOTHING",
            UUID.randomUUID(), code, description);
    }
}
