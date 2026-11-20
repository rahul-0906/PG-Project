package com.pgcrm.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlatformAdminAuthFilterTest {

    private static final String TEST_ADMIN_KEY = "super-secret-platform-key-998877";

    @InjectMocks
    private PlatformAdminAuthFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "configuredAdminKey", TEST_ADMIN_KEY);
        SecurityContextHolder.clearContext();
    }

    @Test
    void testNonPlatformRoute_PassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/inventory/buildings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testPlatformRoute_MissingKey_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform/api/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing or invalid X-Platform-Key"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testPlatformRoute_InvalidKey_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform/api/tenants");
        request.addHeader("X-Platform-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing or invalid X-Platform-Key"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testPlatformRoute_ValidKey_AuthenticatesPlatformAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/platform/api/tenants");
        request.addHeader("X-Platform-Key", TEST_ADMIN_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("platform-admin", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN")));
    }
}
