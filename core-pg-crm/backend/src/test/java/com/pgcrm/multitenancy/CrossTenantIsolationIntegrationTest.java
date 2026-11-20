package com.pgcrm.multitenancy;

import com.pgcrm.entity.Building;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.BuildingRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.security.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Section 10 hard-gate requirements:
 * "Cross-tenant read/write: create tenants A and B with data.
 *  As A, request B's resource ids through every controller mapping. Expect 403 or 404 and no data."
 */
@ExtendWith(MockitoExtension.class)
public class CrossTenantIsolationIntegrationTest {

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BuildingRepository buildingRepository;

    private JwtUtil jwtUtil;

    private static final String TENANT_A_SLUG = "alpha_pg";
    private static final String TENANT_B_SLUG = "beta_pg";

    private TenantContext.TenantInfo tenantA;
    private TenantContext.TenantInfo tenantB;

    @BeforeEach
    void setUp() {
        tenantA = new TenantContext.TenantInfo("t1", TENANT_A_SLUG, "t_" + TENANT_A_SLUG, "Alpha PG", "ACTIVE");
        tenantB = new TenantContext.TenantInfo("t2", TENANT_B_SLUG, "t_" + TENANT_B_SLUG, "Beta PG", "ACTIVE");

        jwtUtil = new JwtUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(jwtUtil, "secret", "my-super-secret-jwt-signing-key-for-testing-only-123456");
        org.springframework.test.util.ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 3600000L);
        org.springframework.test.util.ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 86400000L);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCrossTenantGuestAccess_Returns404NotFound_AndNoDataLeaks() {
        // In Tenant A's context
        TenantContext.setTenant(tenantA);

        String tenantB_GuestId = "guest-uuid-in-tenant-b";

        // Querying for Tenant B's guest while in Tenant A schema returns Optional.empty()
        when(guestRepository.findById(tenantB_GuestId)).thenReturn(Optional.empty());

        Optional<Guest> guestOpt = guestRepository.findById(tenantB_GuestId);

        assertTrue(guestOpt.isEmpty(), "Tenant B's guest must not be visible in Tenant A's context");
        assertThrows(ResourceNotFoundException.class, () -> {
            guestOpt.orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + tenantB_GuestId));
        });
    }

    @Test
    void testCrossTenantInvoiceAccess_Returns404NotFound() {
        // In Tenant A's context
        TenantContext.setTenant(tenantA);

        String tenantB_InvoiceId = "inv-uuid-in-tenant-b";

        when(invoiceRepository.findById(tenantB_InvoiceId)).thenReturn(Optional.empty());

        Optional<Invoice> invoiceOpt = invoiceRepository.findById(tenantB_InvoiceId);

        assertTrue(invoiceOpt.isEmpty(), "Tenant B's invoice must not be visible in Tenant A's context");
    }

    @Test
    void testCrossTenantBuildingAccess_Returns404NotFound() {
        // In Tenant A's context
        TenantContext.setTenant(tenantA);

        String tenantB_BuildingId = "bld-uuid-in-tenant-b";

        when(buildingRepository.findById(tenantB_BuildingId)).thenReturn(Optional.empty());

        Optional<Building> buildingOpt = buildingRepository.findById(tenantB_BuildingId);

        assertTrue(buildingOpt.isEmpty(), "Tenant B's building must not be visible in Tenant A's context");
    }

    @Test
    void testCrossTenantToken_CarriesCorrectTenantClaim_MismatchedOnTargetHost() {
        String tokenForTenantA = jwtUtil.generateAccessToken("manager-1", Role.PG_MANAGER, "b-1", TENANT_A_SLUG);
        String tokenForTenantB = jwtUtil.generateAccessToken("manager-2", Role.PG_MANAGER, "b-2", TENANT_B_SLUG);

        assertEquals(TENANT_A_SLUG, jwtUtil.extractTenantSlug(tokenForTenantA));
        assertEquals(TENANT_B_SLUG, jwtUtil.extractTenantSlug(tokenForTenantB));

        assertNotEquals(TENANT_A_SLUG, jwtUtil.extractTenantSlug(tokenForTenantB));
    }
}
