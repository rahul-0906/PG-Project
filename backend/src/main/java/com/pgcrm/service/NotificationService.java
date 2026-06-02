package com.pgcrm.service;

import com.pgcrm.config.TwilioConfig;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Notification;
import com.pgcrm.entity.enums.NotificationChannel;
import com.pgcrm.repository.NotificationRepository;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TwilioConfig twilioConfig;
    private final com.pgcrm.repository.UserRepository userRepository;

    @Transactional
    public void alertManager(String buildingId, String messageText) {
        log.info("ALERTING MANAGER for building {}: {}", buildingId, messageText);
        
        // Notify managers of the building
        java.util.List<com.pgcrm.entity.User> managers = userRepository.findByRoleAndBranchId(com.pgcrm.entity.enums.Role.PG_MANAGER, buildingId);
        for (com.pgcrm.entity.User manager : managers) {
            Notification notification = Notification.builder()
                    .user(manager)
                    .channel(NotificationChannel.IN_APP)
                    .message(messageText)
                    .deliveryStatus("DELIVERED")
                    .build();
            notificationRepository.save(notification);

            if (twilioConfig.isEnabled() && manager.getPhone() != null && !manager.getPhone().isBlank()) {
                try {
                    String cleanPhone = manager.getPhone().trim();
                    String toNumber = "whatsapp:" + (cleanPhone.startsWith("+") ? cleanPhone : "+91" + cleanPhone);
                    Message.creator(
                            new PhoneNumber(toNumber),
                            new PhoneNumber(twilioConfig.getWhatsappFrom()),
                            messageText
                    ).create();
                    log.info("WhatsApp alert successfully sent to Manager {} ({})", manager.getFullName(), cleanPhone);
                } catch (Exception e) {
                    log.error("Failed to send WhatsApp alert to Manager {}: {}", manager.getFullName(), e.getMessage());
                }
            } else {
                log.info("Manager alert skipped (no WhatsApp phone or Twilio disabled) for Manager: {}", manager.getFullName());
            }
        }

        // Also notify owners
        java.util.List<com.pgcrm.entity.User> owners = userRepository.findByRole(com.pgcrm.entity.enums.Role.PG_OWNER);
        for (com.pgcrm.entity.User owner : owners) {
            Notification notification = Notification.builder()
                    .user(owner)
                    .channel(NotificationChannel.IN_APP)
                    .message(messageText)
                    .deliveryStatus("DELIVERED")
                    .build();
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void sendInApp(Guest guest, String messageText) {
        Notification notification = Notification.builder()
                .guest(guest)
                .user(guest.getUser())
                .channel(NotificationChannel.IN_APP)
                .message(messageText)
                .deliveryStatus("DELIVERED")
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendWhatsApp(Guest guest, String messageText) {
        // Always save in-app record
        Notification notification = Notification.builder()
                .guest(guest)
                .user(guest.getUser())
                .channel(NotificationChannel.WHATSAPP)
                .message(messageText)
                .build();

        if (twilioConfig.isEnabled() && guest.getWhatsappNumber() != null) {
            try {
                String toNumber = "whatsapp:" + guest.getWhatsappNumber();
                Message.creator(
                        new PhoneNumber(toNumber),
                        new PhoneNumber(twilioConfig.getWhatsappFrom()),
                        messageText
                ).create();
                notification.setDeliveryStatus("SENT");
                log.info("WhatsApp sent to {} for guest {}", guest.getWhatsappNumber(), guest.getId());
            } catch (Exception e) {
                notification.setDeliveryStatus("FAILED");
                log.error("WhatsApp send failed for guest {}: {}", guest.getId(), e.getMessage());
            }
        } else {
            notification.setDeliveryStatus("SKIPPED_NO_CONFIG");
            log.info("WhatsApp skipped (Twilio disabled or no number) for guest {}", guest.getId());
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendBoth(Guest guest, String message) {
        sendInApp(guest, message);
        sendWhatsApp(guest, message);
    }
}
