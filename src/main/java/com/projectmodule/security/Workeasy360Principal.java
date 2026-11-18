package com.projectmodule.security;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Who is signed in, as kept in the server-side session.
 *
 * <p>Deliberately slim: no tokens of any kind. The ID token was verified and spent at sign-in; the
 * session proves who the person is and which organization they were signed in to, and nothing the
 * browser sends afterwards can change either.
 *
 * @param userId         the internal identifier used by every table ({@link ExternalUserId})
 * @param organizationId the Workeasy360 organization the sign-in was pinned to
 * @param issuer         the issuer that vouched for the identity
 * @param subject        the issuer's {@code sub} (what the entitlement API is asked about)
 * @param email          profile data only, may be null
 * @param name           profile data only, may be null
 * @param aal            authenticator assurance level reported by Workeasy360 (1 or 2)
 * @param authTime       when Workeasy360 authenticated the person, if it said
 */
public record Workeasy360Principal(
        UUID userId,
        UUID organizationId,
        String issuer,
        String subject,
        String email,
        String name,
        int aal,
        Instant authTime) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ExternalUserId externalUserId() {
        return ExternalUserId.of(userId);
    }

    public OrganizationId organization() {
        return OrganizationId.of(organizationId);
    }

    @Override
    public String toString() {
        // Never the e-mail address or name: principals end up in log lines.
        return "Workeasy360Principal[user=" + userId + ", organization=" + organizationId + "]";
    }
}
