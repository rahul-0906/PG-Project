package com.pgcrm.service;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        originalContext = SecurityContextHolder.getContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void testLog_AuthenticatedUser() {
        // Arrange
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("owner@example.com");
        when(auth.getName()).thenReturn("owner-uuid");
        
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_PG_OWNER"));
        doReturn(authorities).when(auth).getAuthorities();
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // Act
        auditService.log(AuditAction.GUEST_CHECKIN, "Guest", "guest-uuid", "Checked in guest", "{\"k\":\"v\"}");

        // Assert
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        
        AuditLog saved = logCaptor.getValue();
        assertEquals("owner-uuid", saved.getActorId());
        assertEquals("PG_OWNER", saved.getActorRole());
        assertEquals(AuditAction.GUEST_CHECKIN, saved.getAction());
        assertEquals("Guest", saved.getEntityType());
        assertEquals("guest-uuid", saved.getEntityId());
        assertEquals("Checked in guest", saved.getDescription());
        assertEquals("{\"k\":\"v\"}", saved.getMetadata());
    }

    @Test
    void testLog_UnauthenticatedUser() {
        // Arrange
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        // Act
        auditService.log(AuditAction.MAINTENANCE_CREATED, "Maintenance", "ticket-uuid", "Created ticket");

        // Assert
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        
        AuditLog saved = logCaptor.getValue();
        assertNull(saved.getActorId());
        assertEquals("SYSTEM", saved.getActorRole());
        assertEquals(AuditAction.MAINTENANCE_CREATED, saved.getAction());
        assertEquals("Maintenance", saved.getEntityType());
        assertEquals("ticket-uuid", saved.getEntityId());
        assertEquals("Created ticket", saved.getDescription());
        assertNull(saved.getMetadata());
    }

    @Test
    void testLog_AnonymousUser() {
        // Arrange
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        // Act
        auditService.log(AuditAction.PASSWORD_CHANGED, "User", "user-uuid", "Password changed");

        // Assert
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        
        AuditLog saved = logCaptor.getValue();
        assertNull(saved.getActorId());
        assertEquals("SYSTEM", saved.getActorRole());
    }

    @Test
    void testLog_ExceptionSafety() {
        // Arrange
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        // Simulate db save failure
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database down"));

        // Act & Assert
        // The exception must be caught internally and not propagate to the caller.
        assertDoesNotThrow(() -> {
            auditService.log(AuditAction.INVOICE_GENERATED, "Invoice", "inv-uuid", "Invoice generated");
        });
    }
}
