package com.projectmodule.integration.port;

import com.projectmodule.common.identity.ExternalUserId;
import java.util.Optional;

/**
 * Outbound port to the external User Management module.
 *
 * <p>This module stores only opaque {@link ExternalUserId} values — on {@code Project.ownerId},
 * {@code ProjectMember.userId}, {@code ProjectActivity.actorId} and elsewhere — and never a
 * {@code users} table. This port is how a caller resolves one of those identifiers into
 * something displayable, without this module owning or duplicating user data.
 *
 * <p>Not wired into any existing response in this slice: {@code ProjectResponse},
 * {@code ProjectMemberResponse} and {@code DashboardResponse} still expose the raw identifier,
 * unchanged. Enriching them is a later, separate decision.
 */
public interface UserDirectoryPort {

    /**
     * Looks up a user by id.
     *
     * @return the user's summary, or empty if the user module has no record of it (including
     *         when no real integration is configured)
     */
    Optional<UserSummary> findById(ExternalUserId userId);
}
