package com.projectmodule.notification.application;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.integration.port.EmailPort;
import com.projectmodule.notification.domain.Notification;
import com.projectmodule.notification.domain.NotificationType;
import com.projectmodule.notification.infrastructure.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells a person something: always in the app, and by email when a mail server is configured. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository accountRepository;
    private final EmailPort emailPort;
    private final String appBaseUrl;

    public NotificationService(NotificationRepository notificationRepository,
                               UserAccountRepository accountRepository,
                               EmailPort emailPort,
                               @Value("${projectmodule.app-base-url:http://localhost:8080}") String appBaseUrl) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
        this.emailPort = emailPort;
        this.appBaseUrl = appBaseUrl;
    }

    /** Joins the caller's transaction, so a notification exists only if the change it reports does. */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, UUID projectId, UUID taskId) {
        notificationRepository.save(Notification.create(recipientId, type, title, projectId, taskId));
        if (emailPort.isEnabled()) {
            accountRepository.findById(recipientId)
                    .map(UserAccount::getEmail)
                    .ifPresent(address -> emailPort.send(address, title, emailBody(title, projectId, taskId)));
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(RequestContext context, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(context.requireUserId().value(), pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(RequestContext context) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(context.requireUserId().value());
    }

    /** A notification that is someone else's is reported as not found. */
    @Transactional
    public void markRead(RequestContext context, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(found -> found.getRecipientId().equals(context.requireUserId().value()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead(Instant.now());
    }

    @Transactional
    public void markAllRead(RequestContext context) {
        notificationRepository.markAllRead(context.requireUserId().value(), Instant.now());
    }

    private String emailBody(String title, UUID projectId, UUID taskId) {
        String link = appBaseUrl + "/#/";
        if (projectId != null && taskId != null) {
            link += "projects/" + projectId + "/tasks/" + taskId;
        } else if (projectId != null) {
            link += "projects/" + projectId;
        }
        return title + "\n\nOpen in WorkEasyProject: " + link + "\n";
    }
}
