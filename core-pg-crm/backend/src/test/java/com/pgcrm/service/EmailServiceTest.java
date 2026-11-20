package com.pgcrm.service;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.TenantSettings;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private TenantSettingsService tenantSettingsService;

    @InjectMocks
    private EmailService emailService;

    private Guest guest;

    @BeforeEach
    void setUp() {
        guest = new Guest();
        guest.setId("g1");
        guest.setEmail("john@example.com");
        guest.setFullName("John Doe");

        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@pgcrm.com");
        ReflectionTestUtils.setField(emailService, "fromName", "PG CRM");

        lenient().when(tenantSettingsService.getSettings()).thenReturn(
                TenantSettings.builder()
                        .brandingName("PG CRM")
                        .notificationEmailEnabled(true)
                        .build()
        );
    }

    @Test
    void testSendGuestWelcomeEmail_MailDisabled() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        // Act
        emailService.sendGuestWelcomeEmail(guest, "temp-pass");

        // Assert
        verifyNoInteractions(mailSender);
        verifyNoInteractions(templateEngine);
    }

    @Test
    void testSendGuestWelcomeEmail_MailEnabled_Success() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("welcome-email"), any(Context.class)))
                .thenReturn("<html>Welcome!</html>");

        // Act
        emailService.sendGuestWelcomeEmail(guest, "temp-pass");

        // Assert
        verify(mailSender, times(1)).createMimeMessage();
        verify(templateEngine, times(1)).process(eq("welcome-email"), any(Context.class));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    void testSendGuestWelcomeEmail_TenantNotificationDisabled() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        when(tenantSettingsService.getSettings()).thenReturn(
                TenantSettings.builder()
                        .notificationEmailEnabled(false)
                        .build()
        );

        // Act
        emailService.sendGuestWelcomeEmail(guest, "temp-pass");

        // Assert
        verifyNoInteractions(mailSender);
        verifyNoInteractions(templateEngine);
    }
}
