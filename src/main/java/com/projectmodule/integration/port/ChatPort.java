package com.projectmodule.integration.port;

import com.projectmodule.common.identity.OrganizationId;

/**
 * Outbound port to the external Chat module — the "Chat Service" integration point named in
 * {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>This module owns no channel, thread or message history. {@code channelReference} is an
 * opaque identifier meaningful only to the Chat module, exactly like {@link OrganizationId} is
 * opaque here.
 */
public interface ChatPort {

    /** Requests that {@code message} be posted to the given channel. Fire-and-forget. */
    void postMessage(OrganizationId organizationId, String channelReference, String message);
}
