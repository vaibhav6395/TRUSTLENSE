package io.trustlens.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();
    private final byte[] encryptionKey;

    public TotpService(@Value("${app.mfa.encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.encryptionKey = null;
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedKey);
                if (decoded.length != 32) throw new IllegalArgumentException("MFA_ENCRYPTION_KEY must decode to exactly 32 bytes");
                this.encryptionKey = decoded;
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("MFA_ENCRYPTION_KEY must be base64 for a 32-byte key", ex);
            }
        }
    }

    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not protect MFA secret", ex);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt MFA secret; check MFA_ENCRYPTION_KEY", ex);
        }
    }

    public boolean verify(String secret, String suppliedCode) {
        if (suppliedCode == null || !suppliedCode.matches("\\d{6}")) return false;
        long current = Instant.now().getEpochSecond() / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (totp(secret, step).equals(suppliedCode)) return true;
        }
        return false;
    }

    public String provisioningUri(String secret, String username, String issuer) {
        String label = issuer + ":" + username;
        return "otpauth://totp/" + url(label) + "?secret=" + secret + "&issuer=" + url(issuer)
            + "&algorithm=SHA1&digits=6&period=30";
    }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private SecretKeySpec key() {
        if (encryptionKey == null) throw new IllegalStateException("MFA_ENCRYPTION_KEY is required before MFA can be used");
        return new SecretKeySpec(encryptionKey, "AES");
    }

    private String totp(String secret, long step) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception ex) { throw new IllegalStateException("Could not calculate authenticator code", ex); }
    }

    private static String encodeBase32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff); bits += 8;
            while (bits >= 5) { out.append(BASE32.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] decodeBase32(String value) {
        String input = value.replace("=", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : input.toCharArray()) {
            int index = BASE32.indexOf(c);
            if (index < 0) throw new IllegalArgumentException("Invalid TOTP secret");
            buffer = (buffer << 5) | index; bits += 5;
            if (bits >= 8) { out.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        return out.toByteArray();
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
