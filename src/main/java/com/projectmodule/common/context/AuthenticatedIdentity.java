package com.projectmodule.common.context;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;

/** Who a verified credential belongs to. */
public record AuthenticatedIdentity(ExternalUserId userId, OrganizationId organizationId) {
}
