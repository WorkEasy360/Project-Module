package com.projectmodule.notification.infrastructure;

import com.projectmodule.notification.domain.Notification;
import com.projectmodule.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByRecipientIdAndTaskIdAndTypeAndCreatedAtAfter(
            UUID recipientId, UUID taskId, NotificationType type, Instant after);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);

    List<Notification> findByRecipientIdAndIdIn(UUID recipientId, List<UUID> ids);
}
