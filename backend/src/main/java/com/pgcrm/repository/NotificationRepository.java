package com.pgcrm.repository;

import com.pgcrm.entity.Notification;
import com.pgcrm.entity.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByGuestIdAndReadFalse(String guestId);
    List<Notification> findByGuestIdOrderBySentAtDesc(String guestId);
    List<Notification> findByGuestIdAndChannel(String guestId, NotificationChannel channel);
    long countByGuestIdAndReadFalse(String guestId);

    List<Notification> findByUserIdOrderBySentAtDesc(String userId);
    List<Notification> findByUserIdAndReadFalse(String userId);
    long countByUserIdAndReadFalse(String userId);
}
