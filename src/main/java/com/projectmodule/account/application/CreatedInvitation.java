package com.projectmodule.account.application;

import com.projectmodule.account.domain.WorkspaceInvitation;

/** A new invitation, and its token, which is only ever available at this moment. */
public record CreatedInvitation(WorkspaceInvitation invitation, String token) {
}
