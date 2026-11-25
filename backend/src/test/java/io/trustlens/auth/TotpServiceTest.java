package io.trustlens.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    private TotpService service() {
        return new TotpService(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void encryptsAndDecryptsAuthenticatorSecrets() {
        TotpService service = service();
        String secret = service.newSecret();
        assertEquals(secret, service.decrypt(service.encrypt(secret)));
        assertTrue(secret.matches("[A-Z2-7]{32}"));
    }

    @Test
    void acceptsCurrentTimeBasedCode() {
        TotpService service = service();
        String secret = service.newSecret();
        assertTrue(service.verify(secret, service.currentCode(secret)));
    }

    @Test
    void rejectsMalformedCodes() {
        TotpService service = service();
        assertFalse(service.verify(service.newSecret(), "12AB56"));
        assertFalse(service.verify(service.newSecret(), "12345"));
    }

    @Test
    void refusesToUseAnUnconfiguredEncryptionKey() {
        TotpService service = new TotpService("");
        assertThrows(IllegalStateException.class, () -> service.encrypt(service.newSecret()));
    }
}
