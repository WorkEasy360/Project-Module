package com.projectmodule.account.api.dto;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import java.util.UUID;

public record AccountResponse(UUID id, UUID organizationId, String email, String name, WorkspaceRole workspaceRole) {

    public static AccountResponse from(UserAccount account) {
        return new AccountResponse(account.getId(), account.getOrganizationId(),
                account.getEmail(), account.getDisplayName(), account.getWorkspaceRole());
    }
}
