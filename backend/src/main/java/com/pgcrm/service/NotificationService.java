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

    @Transactional
    public void sendInApp(Guest guest, String messageText) {
        Notification notification = Notification.builder()
                .guest(guest)
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
