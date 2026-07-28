package com.pgcrm.service;

import com.pgcrm.config.MetaWhatsAppConfig;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Notification;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.NotificationChannel;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.NotificationRepository;
import com.pgcrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private MetaWhatsAppConfig metaWhatsAppConfig;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User manager;
    private User owner;

    @BeforeEach
    void setUp() {
        manager = new User();
        manager.setId("mgr1");
        manager.setRole(Role.PG_MANAGER);
        manager.setPhone("9876543210");
        manager.setFullName("Manager One");

        owner = new User();
        owner.setId("owner1");
        owner.setRole(Role.PG_OWNER);
        owner.setFullName("Owner One");
    }

    @Test
    void testAlertManager_InAppOnly() {
        // Arrange
        when(userRepository.findByRoleAndBranchId(Role.PG_MANAGER, "bld1"))
                .thenReturn(List.of(manager));
        when(userRepository.findByRole(Role.PG_OWNER))
                .thenReturn(List.of(owner));
        when(metaWhatsAppConfig.isEnabled()).thenReturn(false);

        // Act
        notificationService.alertManager("bld1", "Test Alert Message");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        assertEquals(2, saved.size());

        Notification managerNotif = saved.get(0);
        assertEquals(manager, managerNotif.getUser());
        assertEquals(NotificationChannel.IN_APP, managerNotif.getChannel());
        assertEquals("Test Alert Message", managerNotif.getMessage());

        Notification ownerNotif = saved.get(1);
        assertEquals(owner, ownerNotif.getUser());
        assertEquals(NotificationChannel.IN_APP, ownerNotif.getChannel());
    }

    @Test
    void testSendBoth_InAppOnly() {
        // Arrange
        Guest guest = new Guest();
        guest.setId("g1");
        User guestUser = new User();
        guestUser.setId("gu1");
        guest.setUser(guestUser);
        guest.setFullName("Guest One");

        when(metaWhatsAppConfig.isEnabled()).thenReturn(false);

        // Act
        notificationService.sendBoth(guest, "Hello Guest");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());

        List<Notification> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(NotificationChannel.IN_APP, saved.get(0).getChannel());
        assertEquals("Hello Guest", saved.get(0).getMessage());
    }
}
