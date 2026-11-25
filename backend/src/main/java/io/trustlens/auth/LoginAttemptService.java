package io.trustlens.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {
    private final JdbcTemplate jdbc;
    public LoginAttemptService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private String key(String username, String remoteAddress) {
        try {
            String material = username.trim().toLowerCase() + "|" + remoteAddress;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public boolean isBlocked(String username, String remoteAddress) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM auth_login_attempts
            WHERE key_hash=? AND locked_until > now()
            """, Integer.class, key(username, remoteAddress));
        return count != null && count > 0;
    }

    @Transactional
    public void failed(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        jdbc.update("""
            INSERT INTO auth_login_attempts(key_hash,failure_count,window_started_at,locked_until,updated_at)
            VALUES (?,1,now(),NULL,now())
            ON CONFLICT(key_hash) DO UPDATE SET
              failure_count = CASE WHEN auth_login_attempts.window_started_at < now() - interval '15 minutes'
                                   THEN 1 ELSE auth_login_attempts.failure_count + 1 END,
              window_started_at = CASE WHEN auth_login_attempts.window_started_at < now() - interval '15 minutes'
                                        THEN now() ELSE auth_login_attempts.window_started_at END,
              locked_until = CASE WHEN
                  (CASE WHEN auth_login_attempts.window_started_at < now() - interval '15 minutes'
                        THEN 1 ELSE auth_login_attempts.failure_count + 1 END) >= 5
                  THEN now() + interval '15 minutes' ELSE NULL END,
              updated_at=now()
            """, key);
    }

    @Transactional
    public void succeeded(String username, String remoteAddress) {
        jdbc.update("DELETE FROM auth_login_attempts WHERE key_hash=?", key(username, remoteAddress));
    }
}
