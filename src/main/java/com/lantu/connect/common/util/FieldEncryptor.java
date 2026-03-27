package com.lantu.connect.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field-level encryption for sensitive data at rest
 * (e.g. API key secrets, auth configs, personal data).
 */
@Slf4j
@Component
public class FieldEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${lantu.security.encryption-key:}")
    private String encryptionKey;

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        if (!isEnabled()) {
            return plaintext;
        }
        try {
            byte[] key = deriveKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return "ENC:" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("Field encryption failed", e);
            return plaintext;
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith("ENC:")) {
            return ciphertext;
        }
        if (!isEnabled()) {
            return ciphertext;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext.substring(4));
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            byte[] key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Field decryption failed", e);
            return ciphertext;
        }
    }

    private boolean isEnabled() {
        return encryptionKey != null && !encryptionKey.isBlank();
    }

    private byte[] deriveKey() {
        byte[] raw = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, 32));
        return key;
    }
}
