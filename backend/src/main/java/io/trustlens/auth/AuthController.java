package io.trustlens.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JdbcTemplate jdbc;
    private final MfaChallengeService challenges;
    private final UserDetailsService users;
    private final LoginAttemptService loginAttempts;

    public AuthController(AuthenticationManager authenticationManager, JdbcTemplate jdbc,
                          MfaChallengeService challenges, UserDetailsService users,
                          LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.jdbc = jdbc;
        this.challenges = challenges;
        this.users = users;
        this.loginAttempts = loginAttempts;
    }

    public record LoginRequest(String username, String password) {}
    public record MfaSetupRequest(String username, String challengeToken) {}
    public record MfaVerifyRequest(String username, String challengeToken, String code) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (body.username() == null || body.password() == null) {
            throw new IllegalArgumentException("Username and password are required");
        }
        String username = body.username().trim();
        String remoteAddress = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (loginAttempts.isBlocked(username, remoteAddress))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Try again in 15 minutes.");
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, body.password())
            );
            loginAttempts.succeeded(username, remoteAddress);
        } catch (AuthenticationException ex) {
            loginAttempts.failed(username, remoteAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Boolean enabled = jdbc.queryForObject("SELECT mfa_enabled FROM app_users WHERE username=? AND enabled=TRUE",
            Boolean.class, authenticated.getName());
        String purpose = Boolean.TRUE.equals(enabled) ? "LOGIN" : "ENROLL";
        MfaChallengeService.Challenge challenge = challenges.create(authenticated.getName(), purpose);
        return Map.of("authenticated", false, "mfaRequired", "LOGIN".equals(purpose),
            "mfaEnrollmentRequired", "ENROLL".equals(purpose),
            "username", authenticated.getName(), "challengeToken", challenge.token());
    }

    @PostMapping("/mfa/setup")
    public MfaChallengeService.Setup setupMfa(@RequestBody MfaSetupRequest body) {
        if (body.username() == null || body.challengeToken() == null)
            throw new IllegalArgumentException("Username and MFA challenge are required");
        return challenges.setup(body.challengeToken(), body.username());
    }

    @PostMapping("/mfa/verify")
    public Map<String, Object> verifyMfa(@RequestBody MfaVerifyRequest body,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (body.username() == null || body.challengeToken() == null || body.code() == null)
            throw new IllegalArgumentException("Username, challenge and authenticator code are required");
        challenges.verify(body.challengeToken(), body.code(), body.username());
        UserDetails principal = users.loadUserByUsername(body.username());
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticated);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return Map.of("authenticated", true, "username", authenticated.getName(),
            "authorities", authenticated.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        return Map.of("authenticated", authentication != null && authentication.isAuthenticated(),
            "username", authentication == null ? "" : authentication.getName(),
            "authorities", authentication == null ? java.util.List.of() :
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }
}
