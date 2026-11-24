package io.trustlens.document;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documents;
    public DocumentController(DocumentService documents) { this.documents = documents; }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return documents.listVisible(authentication.getName());
    }

    @GetMapping("/{id}")
    public Map<String, Object> read(@PathVariable UUID id, Authentication authentication) {
        return documents.read(id, authentication.getName(), "READ");
    }

    @PostMapping("/{id}/access-check")
    public Map<String, Object> check(@PathVariable UUID id,
                                    @RequestBody AccessCheckRequest request,
                                    Authentication authentication) {
        return documents.check(id, authentication.getName(), request.action());
    }

    public record AccessCheckRequest(String action) {}
}
