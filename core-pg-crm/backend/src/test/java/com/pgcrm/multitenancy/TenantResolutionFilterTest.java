package com.pgcrm.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private FilterChain filterChain;

    private DeploymentProperties deploymentProperties;
    private TenantResolutionFilter filter;

    @BeforeEach
    void setUp() {
        deploymentProperties = new DeploymentProperties();
        filter = new TenantResolutionFilter(deploymentProperties, tenantRegistryService);
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void testDedicatedModeResolvesDedicatedTenantIgnoringHost() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.DEDICATED);
        deploymentProperties.setDedicatedTenant("default");

        TenantContext.TenantInfo defaultTenant = new TenantContext.TenantInfo(
                "id-def", "default", "t_default", "Default PG", "ACTIVE"
        );
        when(tenantRegistryService.findTenantBySlug("default")).thenReturn(Optional.of(defaultTenant));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/config/public");
        request.addHeader("Host", "random-domain.com:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertEquals("default", TenantContext.getCurrentTenantSlug());
            assertEquals("t_default", TenantContext.getCurrentTenantSchema());
            assertEquals("default", MDC.get("tenant"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        assertNull(TenantContext.getCurrentTenant());
        assertNull(MDC.get("tenant"));
    }

    @Test
    void testSharedModeResolvesByHost() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);
        deploymentProperties.setBaseDomain("pgcrm.com");

        TenantContext.TenantInfo tenant = new TenantContext.TenantInfo(
                "id-sri", "sri_sai", "t_sri_sai", "Sri Sai PG", "ACTIVE"
        );
        when(tenantRegistryService.resolveTenantByHost("sri_sai.pgcrm.com")).thenReturn(Optional.of(tenant));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/guests");
        request.addHeader("Host", "sri_sai.pgcrm.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertEquals("sri_sai", TenantContext.getCurrentTenantSlug());
            assertEquals("sri_sai", MDC.get("tenant"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testSharedModeUnknownHostReturns404() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);

        when(tenantRegistryService.resolveTenantByHost("unknown.pgcrm.com")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/guests");
        request.addHeader("Host", "unknown.pgcrm.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("Tenant not found"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testSharedModeSuspendedTenantReturns403() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);

        TenantContext.TenantInfo suspended = new TenantContext.TenantInfo(
                "id-susp", "bad_tenant", "t_bad_tenant", "Suspended PG", "SUSPENDED"
        );
        when(tenantRegistryService.resolveTenantByHost("bad.pgcrm.com")).thenReturn(Optional.of(suspended));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/invoices");
        request.addHeader("Host", "bad.pgcrm.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("suspended"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testPlatformAdminEndpointDisabledInDedicatedMode() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.DEDICATED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/platform/api/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("not available in dedicated mode"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testPlatformAdminEndpointRejectedOnTenantHostInSharedMode() throws ServletException, IOException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);
        deploymentProperties.setBaseDomain("pgcrm.com");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/platform/api/tenants");
        request.addHeader("Host", "sri_sai.pgcrm.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("not reachable on a tenant host"));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
