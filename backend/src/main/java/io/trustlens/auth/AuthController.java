package io.trustlens.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public record LoginRequest(String username, String password) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest body,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        if (body.username() == null || body.password() == null) {
            throw new IllegalArgumentException("Username and password are required");
        }
        Authentication authenticated = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password())
        );
        // Rotate the existing session ID after authentication to mitigate session fixation.
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
