package com.pgcrm.service;

import com.pgcrm.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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

    @Test
    void testTenantIsolation_SameUserIdAcrossDifferentTenants() {
        String userId = "common_user_id";
        String emailA = "tenant_a_user@example.com";
        String emailB = "tenant_b_user@example.com";
        String codeA = "111111";
        String codeB = "222222";

        // 1. Store in Tenant A
        TenantContext.setTenant(new TenantContext.TenantInfo("t1", "tenant_a", "t_tenant_a", "Tenant A", "ACTIVE"));
        emailVerificationService.storeCode(userId, emailA, codeA);

        // 2. Store in Tenant B for same userId
        TenantContext.setTenant(new TenantContext.TenantInfo("t2", "tenant_b", "t_tenant_b", "Tenant B", "ACTIVE"));
        emailVerificationService.storeCode(userId, emailB, codeB);

        // 3. Switch back to Tenant A
        TenantContext.setTenant(new TenantContext.TenantInfo("t1", "tenant_a", "t_tenant_a", "Tenant A", "ACTIVE"));
        // Tenant A must verify with codeA, codeB must fail
        assertFalse(emailVerificationService.verifyCode(userId, emailA, codeB));
        assertTrue(emailVerificationService.verifyCode(userId, emailA, codeA));

        // 4. Switch to Tenant B
        TenantContext.setTenant(new TenantContext.TenantInfo("t2", "tenant_b", "t_tenant_b", "Tenant B", "ACTIVE"));
        // Tenant B must still have codeB intact
        assertTrue(emailVerificationService.verifyCode(userId, emailB, codeB));
    }
}
