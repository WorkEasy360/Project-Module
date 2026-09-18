package com.projectmodule.integration.port;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;

/**
 * Outbound port to the external Notification module — the "Notification Service" integration
 * point named in {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>This module never sends a notification directly to a user; it asks this port to. The
 * durable, correct record of "what happened" is already the transactional outbox
 * ({@code OutboxMessage}, written atomically with the state change); this port is for a future
 * caller — such as an outbox publisher — that has decided a specific person should be told
 * about it, not a replacement for the outbox.
 */
public interface NotificationPort {

    /**
     * Requests that {@code recipientId} be notified. Fire-and-forget from this module's point of
     * view: delivery, retry and read-state all belong to the Notification module.
     */
    void notify(OrganizationId organizationId, ExternalUserId recipientId, String subject, String message);
}
