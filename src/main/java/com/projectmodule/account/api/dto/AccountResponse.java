package com.projectmodule.account.api.dto;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import java.util.UUID;

/** avatarVersion changes whenever the photo does (null when there is none), so clients know when to fetch it. */
public record AccountResponse(UUID id, UUID organizationId, String email, String name, WorkspaceRole workspaceRole,
                              String avatarVersion) {

    public static AccountResponse from(UserAccount account) {
        return new AccountResponse(account.getId(), account.getOrganizationId(),
                account.getEmail(), account.getDisplayName(), account.getWorkspaceRole(),
                account.getAvatarUpdatedAt().map(t -> Long.toString(t.toEpochMilli())).orElse(null));
    }
}
