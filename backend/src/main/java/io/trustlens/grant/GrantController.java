package io.trustlens.grant;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/grants")
public class GrantController {
    private final GrantService grants;
    public GrantController(GrantService grants) { this.grants = grants; }

    @PostMapping("/{id}/revoke")
    public Map<String, Object> revoke(@PathVariable UUID id, Authentication authentication) {
        return grants.revoke(id, authentication.getName());
    }
}
