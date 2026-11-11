package com.projectmodule.notification.api;

import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.notification.domain.Notification;
import com.projectmodule.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own notifications. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    public record NotificationResponse(UUID id, NotificationType type, String title, UUID projectId, UUID taskId,
                                       boolean read, Instant createdAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                    notification.getProjectId().orElse(null), notification.getTaskId().orElse(null),
                    notification.isRead(), notification.getCreatedAt());
        }
    }

    private final NotificationService notificationService;
    private final RequestContext requestContext;

    public NotificationController(NotificationService notificationService, RequestContext requestContext) {
        this.notificationService = notificationService;
        this.requestContext = requestContext;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<Notification> page = notificationService.list(requestContext, pageable);
        return PageResponse.of(page, page.map(NotificationResponse::from).getContent());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(requestContext));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(requestContext, id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead(requestContext);
    }
}
