package com.pgcrm.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EmailVerificationServiceTest {

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService();
    }

    @Test
    void testStoreAndVerifyCode_Success() {
        String userId = "user1";
        String newEmail = "new@example.com";
        String code = "123456";

        // Store the code
        emailVerificationService.storeCode(userId, newEmail, code);

        // Verify with matching credentials (should succeed and consume)
        assertTrue(emailVerificationService.verifyCode(userId, newEmail, code));

        // Re-verifying should fail because the code was consumed (single-use)
        assertFalse(emailVerificationService.verifyCode(userId, newEmail, code));
    }

    @Test
    void testVerifyCode_CaseInsensitiveEmail() {
        String userId = "user1";
        String newEmail = "New@Example.com";
        String code = "123456";

        emailVerificationService.storeCode(userId, newEmail, code);

        // Verify with lowercase email (should succeed)
        assertTrue(emailVerificationService.verifyCode(userId, "new@example.com", code));
    }

    @Test
    void testVerifyCode_IncorrectCode() {
        String userId = "user1";
        String newEmail = "new@example.com";
        String code = "123456";

        emailVerificationService.storeCode(userId, newEmail, code);

        // Submit wrong code (should fail but NOT consume)
        assertFalse(emailVerificationService.verifyCode(userId, newEmail, "wrongcode"));

        // Submit correct code (should now succeed)
        assertTrue(emailVerificationService.verifyCode(userId, newEmail, code));
    }

    @Test
    void testVerifyCode_NoPendingRequest() {
        assertFalse(emailVerificationService.verifyCode("nonexistent", "new@example.com", "123456"));
    }

    @Test
    void testVerifyCode_ExpiredCode() {
        String userId = "user1";
        String newEmail = "new@example.com";
        String code = "123456";

        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 12, 0);

        try (MockedStatic<LocalDateTime> mockedTime = mockStatic(LocalDateTime.class)) {
            // Mock time to baseTime when storing
            mockedTime.when(LocalDateTime::now).thenReturn(baseTime);
            emailVerificationService.storeCode(userId, newEmail, code);

            // Mock time to 16 minutes later when verifying (exceeding 15 minute TTL)
            LocalDateTime expiredTime = baseTime.plusMinutes(16);
            mockedTime.when(LocalDateTime::now).thenReturn(expiredTime);

            // Verify should fail due to expiration
            assertFalse(emailVerificationService.verifyCode(userId, newEmail, code));
        }

        // Verify has evicted the code from cache, even verifying at normal time now should fail
        assertFalse(emailVerificationService.verifyCode(userId, newEmail, code));
    }
}
