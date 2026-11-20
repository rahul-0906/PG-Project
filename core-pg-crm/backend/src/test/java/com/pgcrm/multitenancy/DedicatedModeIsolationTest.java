package com.pgcrm.multitenancy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Section 10 requirement:
 * "Dedicated mode: the same suite's key cases pass with app.deployment-mode=dedicated."
 */
@ExtendWith(MockitoExtension.class)
public class DedicatedModeIsolationTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private FilterChain filterChain;

    private DeploymentProperties deploymentProperties;
    private TenantResolutionFilter filter;

    private static final String DEDICATED_SLUG = "sri_sai";
    private static final TenantContext.TenantInfo DEDICATED_TENANT = new TenantContext.TenantInfo(
            "d1", DEDICATED_SLUG, "t_" + DEDICATED_SLUG, "Sri Sai Dedicated", "ACTIVE"
    );

    @BeforeEach
    void setUp() {
        deploymentProperties = new DeploymentProperties();
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.DEDICATED);
        deploymentProperties.setDedicatedTenant(DEDICATED_SLUG);

        filter = new TenantResolutionFilter(deploymentProperties, tenantRegistryService);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testDedicatedMode_ResolvesDedicatedTenant_RegardlessOfHostHeader() throws Exception {
        when(tenantRegistryService.findTenantBySlug(DEDICATED_SLUG)).thenReturn(Optional.of(DEDICATED_TENANT));

        // Request with arbitrary custom domain or localhost
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/config/public");
        request.addHeader("Host", "custom-client-domain.org:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Filter should continue the chain
        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void testDedicatedMode_PlatformAdminApi_Returns404() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform/api/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Chain MUST NOT proceed, returns 404
        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("Platform admin API is not available in dedicated mode"));
    }
}
