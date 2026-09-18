package com.projectmodule.integration.adapter;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.integration.port.UserDirectoryPort;
import com.projectmodule.integration.port.UserSummary;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link UserDirectoryPort}: the User Management module does not exist
 * yet, so there is nothing to look up. Logs the call and returns empty — it never invents a
 * display name.
 */
@Component
public class NoopUserDirectoryAdapter implements UserDirectoryPort {

    private static final Logger log = LoggerFactory.getLogger(NoopUserDirectoryAdapter.class);

    @Override
    public Optional<UserSummary> findById(ExternalUserId userId) {
        log.debug("UserDirectoryPort.findById({}): no User module integration is configured; "
                + "returning empty rather than a fabricated user", userId);
        return Optional.empty();
    }
}
