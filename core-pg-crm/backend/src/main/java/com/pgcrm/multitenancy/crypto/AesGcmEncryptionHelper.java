package com.pgcrm.multitenancy.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM (Galois/Counter Mode) encryption helper for per-tenant sensitive secrets.
 *
 * <p><strong>Cryptographic Specifications:</strong></p>
 * <ul>
 *   <li>Algorithm: {@code AES/GCM/NoPadding}</li>
 *   <li>Key Size: 256 bits (32 bytes)</li>
 *   <li>Initialization Vector (IV): 96 bits (12 bytes), cryptographically random per encryption</li>
 *   <li>Authentication Tag: 128 bits</li>
 *   <li>Wire Format: {@code v1:<Base64( IV (12 bytes) || Ciphertext + Tag )>}</li>
 *   <li>Key Source: Injected from environment variable {@code APP_ENCRYPTION_KEY}.
 *       Never stored in database or source control.</li>
 * </ul>
 */
@Component
@Slf4j
public class AesGcmEncryptionHelper {

    public static final String VERSION_PREFIX = "v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionHelper(
            @Value("${app.encryption-key:}") String base64Key) {

        if (base64Key != null && !base64Key.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(base64Key.trim());
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                        "APP_ENCRYPTION_KEY must be a 256-bit (32-byte) Base64 encoded string, got " + decoded.length + " bytes"
                );
            }
            this.secretKey = new SecretKeySpec(decoded, "AES");
            log.info("✓ AES-GCM Encryption Helper initialized with configured 256-bit master key.");
        } else {
            // Development fallback master key (deterministic 32-byte key for local dev & testing)
            byte[] devKey = "DevPgCrmMasterKey32BytesLongAes!".getBytes(StandardCharsets.UTF_8);
            this.secretKey = new SecretKeySpec(devKey, "AES");
            log.warn("⚠️ APP_ENCRYPTION_KEY not set. Using development master key. Set APP_ENCRYPTION_KEY in production!");
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a fresh random 12-byte IV.
     *
     * @param plaintext unencrypted string
     * @return Version-prefixed Base64 string {@code v1:<base64>}, or null/empty if input is null/blank
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.isBlank()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt secret: {}", e.getMessage());
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts version-prefixed Base64 AES-GCM string.
     *
     * @param encrypted encrypted string (with {@code v1:} prefix, or raw string if legacy/unencrypted)
     * @return decrypted plaintext, or null if input is null
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        if (encrypted.isBlank()) {
            return encrypted;
        }

        // If not prefixed with v1:, treat as unencrypted legacy value
        if (!encrypted.startsWith(VERSION_PREFIX)) {
            return encrypted;
        }

        String payload = encrypted.substring(VERSION_PREFIX.length()).trim();

        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            if (decoded.length < GCM_IV_LENGTH_BYTES) {
                throw new IllegalStateException("Ciphertext too short to contain IV and tag");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for ciphertext payload: {}", e.getMessage());
            throw new IllegalStateException("Decryption failed: corrupted ciphertext or invalid key", e);
        }
    }
}
