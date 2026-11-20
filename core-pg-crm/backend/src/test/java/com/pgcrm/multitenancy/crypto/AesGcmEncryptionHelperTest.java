package com.pgcrm.multitenancy.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class AesGcmEncryptionHelperTest {

    // Valid 256-bit (32 bytes) Base64-encoded key
    private static final String VALID_KEY_BASE64 = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes()
    );

    @Test
    void testEncryptDecrypt_RoundTrip() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper(VALID_KEY_BASE64);

        String secret = "rzp_live_secret_key_998877";
        String encrypted = helper.encrypt(secret);

        assertNotNull(encrypted);
        assertNotEquals(secret, encrypted);
        assertTrue(encrypted.startsWith("v1:"));

        String decrypted = helper.decrypt(encrypted);
        assertEquals(secret, decrypted);
    }

    @Test
    void testNullAndBlankHandling() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper(VALID_KEY_BASE64);

        assertNull(helper.encrypt(null));
        assertNull(helper.decrypt(null));

        assertEquals("", helper.encrypt(""));
        assertEquals("   ", helper.encrypt("   "));
        assertEquals("", helper.decrypt(""));
        assertEquals("   ", helper.decrypt("   "));
    }

    @Test
    void testRandomIvProducesDifferentCiphertexts() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper(VALID_KEY_BASE64);

        String secret = "constant_secret";
        String enc1 = helper.encrypt(secret);
        String enc2 = helper.encrypt(secret);

        assertNotEquals(enc1, enc2, "Each encryption must use a unique IV");
        assertEquals(secret, helper.decrypt(enc1));
        assertEquals(secret, helper.decrypt(enc2));
    }

    @Test
    void testDecryptUnencryptedPassThrough() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper(VALID_KEY_BASE64);

        // String without v1: prefix is returned as-is for backward compatibility
        String legacyPlaintext = "raw_legacy_secret";
        assertEquals(legacyPlaintext, helper.decrypt(legacyPlaintext));
    }

    @Test
    void testTamperedCiphertext_FailsAuthentication() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper(VALID_KEY_BASE64);

        String secret = "confidential_data";
        String encrypted = helper.encrypt(secret);

        // Tamper with payload
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "XXXX";

        assertThrows(IllegalStateException.class, () -> helper.decrypt(tampered));
    }

    @Test
    void testWrongKey_FailsDecryption() {
        AesGcmEncryptionHelper helper1 = new AesGcmEncryptionHelper(VALID_KEY_BASE64);
        String otherKeyBase64 = Base64.getEncoder().encodeToString(
                "abcdefghijklmnopqrstuvwxyz012345".getBytes()
        );
        AesGcmEncryptionHelper helper2 = new AesGcmEncryptionHelper(otherKeyBase64);

        String secret = "secret_message";
        String encrypted = helper1.encrypt(secret);

        assertThrows(IllegalStateException.class, () -> helper2.decrypt(encrypted));
    }

    @Test
    void testDefaultKeyGeneration_WhenEmptyKeyProvided() {
        AesGcmEncryptionHelper helper = new AesGcmEncryptionHelper("");
        String secret = "generated_key_secret";
        String enc = helper.encrypt(secret);
        assertEquals(secret, helper.decrypt(enc));
    }

    @Test
    void testInvalidKeyLength_ThrowsException() {
        // 16 bytes = 128 bit, but 256-bit required
        String shortKey = Base64.getEncoder().encodeToString("1234567890123456".getBytes());
        assertThrows(IllegalArgumentException.class, () -> new AesGcmEncryptionHelper(shortKey));
    }
}
