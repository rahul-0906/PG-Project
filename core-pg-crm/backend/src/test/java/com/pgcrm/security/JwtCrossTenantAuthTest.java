package com.pgcrm.security;

import com.pgcrm.entity.enums.Role;
import com.pgcrm.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtCrossTenantAuthTest {

    @Spy
    private JwtUtil jwtUtil = new JwtUtil();

    @InjectMocks
    private JwtAuthenticationFilter jwtFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "my-super-secret-jwt-signing-key-for-testing-only-123456");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 86400000L);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSameTenantToken_PassesFilter() throws Exception {
        TenantContext.setTenant(new TenantContext.TenantInfo(
                "t1", "alpha_pg", "t_alpha_pg", "Alpha PG", "ACTIVE"
        ));

        String token = jwtUtil.generateAccessToken("user-1", Role.PG_MANAGER, "b-1", "alpha_pg");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/manager/guests");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(request, response, filterChain);

        // Filter should continue the chain
        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void testCrossTenantToken_RejectedWith401() throws Exception {
        // Request host resolved to beta_pg
        TenantContext.setTenant(new TenantContext.TenantInfo(
                "t2", "beta_pg", "t_beta_pg", "Beta PG", "ACTIVE"
        ));

        // But token was issued for alpha_pg
        String tokenFromAlpha = jwtUtil.generateAccessToken("user-1", Role.PG_MANAGER, "b-1", "alpha_pg");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/manager/guests");
        request.addHeader("Authorization", "Bearer " + tokenFromAlpha);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtFilter.doFilterInternal(request, response, filterChain);

        // Chain MUST NOT proceed
        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Unauthorized: Token tenant does not match request tenant host"));
    }
}
