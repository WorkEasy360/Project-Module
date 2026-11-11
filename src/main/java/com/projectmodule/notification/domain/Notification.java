package com.projectmodule.notification.domain;

import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Something one person should know about, shown in the app's notification list. */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 400;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, updatable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "task_id", updatable = false)
    private UUID taskId;

    @Column(name = "read_at")
    private Instant readAt;

    /** For JPA only. */
    protected Notification() {
    }

    private Notification(UUID id, UUID recipientId, NotificationType type, String title, UUID projectId, UUID taskId) {
        super(id);
        this.recipientId = recipientId;
        this.type = type;
        this.title = title.length() > TITLE_MAX_LENGTH ? title.substring(0, TITLE_MAX_LENGTH) : title;
        this.projectId = projectId;
        this.taskId = taskId;
    }

    public static Notification create(UUID recipientId, NotificationType type, String title,
                                      UUID projectId, UUID taskId) {
        return new Notification(UuidV7.generate(), recipientId, type, title, projectId, taskId);
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Optional<UUID> getProjectId() {
        return Optional.ofNullable(projectId);
    }

    public Optional<UUID> getTaskId() {
        return Optional.ofNullable(taskId);
    }

    public boolean isRead() {
        return readAt != null;
    }
}
