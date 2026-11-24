package io.trustlens.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentFileStorage {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
        "application/pdf", ".pdf",
        "text/plain", ".txt",
        "image/png", ".png",
        "image/jpeg", ".jpg"
    );

    private final Path root;

    public DocumentFileStorage(@Value("${app.storage.root:./var/trustlens-files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot initialize private document storage", ex);
        }
    }

    public StoredFile store(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("A non-empty file is required");
        if (upload.getSize() > MAX_BYTES) throw new IllegalArgumentException("File exceeds the 10 MB limit");
        String contentType = upload.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) throw new IllegalArgumentException("Allowed file types: PDF, TXT, PNG and JPEG");
        byte[] bytes = upload.getBytes();
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid file size");
        validateSignature(contentType, bytes);

        String key = UUID.randomUUID() + extension;
        Path target = resolve(key);
        Files.write(target, bytes);
        return new StoredFile(key, contentType, bytes.length, sha256(bytes));
    }

    public Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.(pdf|txt|png|jpg)")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        return target;
    }

    public void deleteQuietly(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (Exception ignored) { }
    }

    private void validateSignature(String type, byte[] b) {
        boolean valid = switch (type) {
            case "application/pdf" -> b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
            case "image/png" -> b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/jpeg" -> b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
            case "text/plain" -> {
                boolean text = true;
                for (byte value : b) if (value == 0) { text = false; break; }
                yield text;
            }
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("File content does not match its declared type");
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record StoredFile(String storageKey, String contentType, long sizeBytes, String sha256) {}
}
