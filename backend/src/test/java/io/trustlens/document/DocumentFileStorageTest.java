package io.trustlens.document;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class DocumentFileStorageTest {
    @TempDir Path temp;

    @Test
    void storesPdfUnderGeneratedPrivateKeyAndReturnsDigest() throws Exception {
        DocumentFileStorage storage = new DocumentFileStorage(temp.toString());
        byte[] bytes = "%PDF-1.7\nsynthetic test document".getBytes(StandardCharsets.UTF_8);
        var upload = new MockMultipartFile("file", "campus.pdf", "application/pdf", bytes);

        var stored = storage.store(upload);

        assertTrue(stored.storageKey().endsWith(".pdf"));
        assertTrue(Files.isRegularFile(storage.resolve(stored.storageKey())));
        assertEquals(bytes.length, stored.sizeBytes());
        assertEquals(64, stored.sha256().length());
    }

    @Test
    void rejectsMismatchedContentSignature() {
        DocumentFileStorage storage = new DocumentFileStorage(temp.toString());
        var upload = new MockMultipartFile("file", "fake.pdf", "application/pdf",
            "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> storage.store(upload));
    }

    @Test
    void rejectsTraversalStorageKeys() {
        DocumentFileStorage storage = new DocumentFileStorage(temp.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../../etc/passwd"));
    }

    @Test
    void rejectsUnsupportedMediaTypes() {
        DocumentFileStorage storage = new DocumentFileStorage(temp.toString());
        var upload = new MockMultipartFile("file", "archive.zip", "application/zip", new byte[]{1,2,3});
        assertThrows(IllegalArgumentException.class, () -> storage.store(upload));
    }
}
