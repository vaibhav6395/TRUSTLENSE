package io.trustlens.auth;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class CurrentUserController {
    @GetMapping("/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        // Principal comes from Spring Security, never from request JSON.
        return Map.of("authenticated", authentication != null && authentication.isAuthenticated(),
                      "username", authentication == null ? "" : authentication.getName());
    }
}
