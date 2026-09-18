package com.projectmodule.integration.port;

import com.projectmodule.common.identity.ExternalUserId;

/**
 * The minimal display information this module needs about a user it does not own.
 *
 * <p>Deliberately small: only what a project view would show next to a raw user id. Anything
 * more belongs to the User Management module, not here.
 */
public record UserSummary(ExternalUserId id, String displayName) {
}
