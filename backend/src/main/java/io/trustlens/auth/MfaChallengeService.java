package io.trustlens.auth;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class MfaChallengeService {
    private final JdbcTemplate jdbc;
    private final TotpService totp;
    private final SecureRandom random = new SecureRandom();

    public MfaChallengeService(JdbcTemplate jdbc, TotpService totp) {
        this.jdbc = jdbc; this.totp = totp;
    }

    public record Challenge(String token, String purpose) {}
    public record Setup(String secret, String otpauthUri) {}

    public Challenge create(String username, String purpose) {
        UUID userId = jdbc.queryForObject("SELECT id FROM app_users WHERE username=? AND enabled=TRUE",
            UUID.class, username);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM auth_mfa_challenges WHERE expires_at < now()");
        jdbc.update("""
            INSERT INTO auth_mfa_challenges(id,user_id,token_hash,purpose,expires_at)
            VALUES (?,?,?,?,now() + interval '5 minutes')
            """, UUID.randomUUID(), userId, TotpService.sha256(token), purpose);
        return new Challenge(token, purpose);
    }

    @Transactional
    public Setup setup(String token, String username) {
        Map<String,Object> challenge = findChallenge(token, "ENROLL", username);
        String secret = totp.newSecret();
        jdbc.update("UPDATE auth_mfa_challenges SET pending_secret_encrypted=? WHERE id=?",
            totp.encrypt(secret), challenge.get("id"));
        return new Setup(secret, totp.provisioningUri(secret, username, "TRUSTLENS"));
    }

    @Transactional
    public void verify(String token, String code, String username) {
        Map<String,Object> challenge = findChallenge(token, null, username);
        String purpose = (String) challenge.get("purpose");
        String encrypted;
        if ("ENROLL".equals(purpose)) encrypted = (String) challenge.get("pending_secret_encrypted");
        else encrypted = jdbc.queryForObject("SELECT mfa_secret_encrypted FROM app_users WHERE username=?",
            String.class, username);
        if (encrypted == null || !totp.verify(totp.decrypt(encrypted), code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticator code");
        }
        if ("ENROLL".equals(purpose)) {
            jdbc.update("""
                UPDATE app_users SET mfa_enabled=TRUE,mfa_secret_encrypted=?,mfa_enrolled_at=now()
                WHERE username=? AND enabled=TRUE
                """, encrypted, username);
        }
        jdbc.update("DELETE FROM auth_mfa_challenges WHERE id=?", challenge.get("id"));
    }

    private Map<String,Object> findChallenge(String token, String expectedPurpose, String username) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA challenge is missing");
        var rows = jdbc.queryForList("""
            SELECT c.id,c.purpose,c.pending_secret_encrypted
            FROM auth_mfa_challenges c JOIN app_users u ON u.id=c.user_id
            WHERE c.token_hash=? AND u.username=? AND u.enabled=TRUE AND c.expires_at > now()
            """, TotpService.sha256(token), username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "MFA challenge expired or invalid");
        Map<String,Object> result = rows.get(0);
        if (expectedPurpose != null && !expectedPurpose.equals(result.get("purpose")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This MFA challenge cannot enroll an authenticator");
        return result;
    }
}
