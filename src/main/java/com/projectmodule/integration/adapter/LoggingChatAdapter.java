package com.projectmodule.integration.adapter;

import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.integration.port.ChatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link ChatPort}: the Chat module does not exist yet, so nothing is
 * actually posted. Logs the request and never claims a message was delivered.
 */
@Component
public class LoggingChatAdapter implements ChatPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatAdapter.class);

    @Override
    public void postMessage(OrganizationId organizationId, String channelReference, String message) {
        log.info("ChatPort.postMessage(): no Chat module integration is configured; not posted. "
                + "organizationId={} channelReference={}", organizationId, channelReference);
    }
}
