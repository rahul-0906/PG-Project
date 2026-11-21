package com.pgcrm.multitenancy.ratelimit;

import com.pgcrm.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantRateLimitingFilterTest {

    private TenantRateLimitingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TenantRateLimitingFilter(true, 5); // 5 requests per minute for testing
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testRequestsWithinLimitPassSuccessfully() throws ServletException, IOException {
        TenantContext.setCurrentTenant("tenant_a", "t_tenant_a");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/guests");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals("5", response.getHeader("X-RateLimit-Limit"));
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void testExceededLimitReturns429TooManyRequests() throws ServletException, IOException {
        TenantContext.setCurrentTenant("tenant_a", "t_tenant_a");

        // Consume all 5 available tokens
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/guests");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // 6th request must be rejected with 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("GET", "/api/guests");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, filterChain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("30", blockedRes.getHeader("Retry-After"));
        assertEquals("5", blockedRes.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        assertTrue(blockedRes.getContentAsString().contains("Too Many Requests"));
        assertTrue(blockedRes.getContentAsString().contains("tenant_a"));

        // filterChain must only have been invoked 5 times, not 6
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    void testTenantRateLimitIsolation() throws ServletException, IOException {
        // Exhaust quota for tenant_a
        TenantContext.setCurrentTenant("tenant_a", "t_tenant_a");
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/guests");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
        }

        // Verify tenant_a is blocked
        MockHttpServletRequest blockedReqA = new MockHttpServletRequest("GET", "/api/guests");
        MockHttpServletResponse blockedResA = new MockHttpServletResponse();
        filter.doFilter(blockedReqA, blockedResA, filterChain);
        assertEquals(429, blockedResA.getStatus());

        // Now switch context to tenant_b
        TenantContext.setCurrentTenant("tenant_b", "t_tenant_b");
        MockHttpServletRequest reqB = new MockHttpServletRequest("GET", "/api/guests");
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(reqB, resB, filterChain);

        // Tenant B must NOT be blocked
        assertEquals(200, resB.getStatus());
        assertEquals("5", resB.getHeader("X-RateLimit-Limit"));
    }

    @Test
    void testExemptPathsBypassRateLimit() throws ServletException, IOException {
        TenantContext.setCurrentTenant("tenant_a", "t_tenant_a");

        // Exhaust quota for tenant_a
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/guests");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
        }

        // /api/health should bypass rate limiter
        MockHttpServletRequest healthReq = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse healthRes = new MockHttpServletResponse();
        filter.doFilter(healthReq, healthRes, filterChain);

        assertEquals(200, healthRes.getStatus());
        assertNull(healthRes.getHeader("X-RateLimit-Limit"));

        // Static asset should also bypass
        MockHttpServletRequest staticReq = new MockHttpServletRequest("GET", "/assets/main.js");
        MockHttpServletResponse staticRes = new MockHttpServletResponse();
        filter.doFilter(staticReq, staticRes, filterChain);

        assertEquals(200, staticRes.getStatus());
    }

    @Test
    void testDisabledFilterPassesAllRequests() throws ServletException, IOException {
        TenantRateLimitingFilter disabledFilter = new TenantRateLimitingFilter(false, 2);
        TenantContext.setCurrentTenant("tenant_a", "t_tenant_a");

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/guests");
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabledFilter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }
    }
}
