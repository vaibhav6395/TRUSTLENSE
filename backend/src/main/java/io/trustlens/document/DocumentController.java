package io.trustlens.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{id}/content")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID id, Authentication authentication) {
        DocumentService.DownloadFile file = documents.download(id, authentication.getName());
        String safeName = file.title().replaceAll("[\\r\\n\\\\/\"]", "_");
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(file.contentType()))
            .contentLength(file.sizeBytes())
            .body(new FileSystemResource(file.path()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file,
                                      @RequestParam("title") String title,
                                      @RequestParam("classification") String classification,
                                      Authentication authentication) throws IOException {
        return documents.upload(file, title, classification, authentication.getName());
    }

    @PostMapping("/{id}/access-check")
    public Map<String, Object> check(@PathVariable UUID id,
                                    @RequestBody AccessCheckRequest request,
                                    Authentication authentication) {
        return documents.check(id, authentication.getName(), request == null ? null : request.action());
    }

    public record AccessCheckRequest(String action) {}
}
