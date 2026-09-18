package com.projectmodule.integration.adapter;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link NotificationPort}: the Notification module does not exist yet,
 * so nothing is actually delivered. Logs the request so it stays observable during development,
 * and never claims a notification was sent.
 */
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void notify(OrganizationId organizationId, ExternalUserId recipientId, String subject, String message) {
        log.info("NotificationPort.notify(): no Notification module integration is configured; "
                + "not delivered. organizationId={} recipientId={} subject={}",
                organizationId, recipientId, subject);
    }
}
