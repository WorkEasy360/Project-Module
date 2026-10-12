package com.projectmodule.security.identity;

import com.projectmodule.common.identity.ExternalUserId;

/**
 * Resolves the internal user for an external identity, creating the mapping on first sign-in.
 *
 * <p>The rule for a new (issuer, subject): the internal id is {@code UUID(subject)} when the subject is a
 * UUID that is not already bound to <em>any</em> identity (of any issuer: the same {@code sub} under another
 * issuer is another person), otherwise a fresh random id. Workeasy360's
 * {@code sub} is its own user id, which is what the retired {@code X-User-Id} header always meant to
 * carry, so rows written before sign-in existed keep their owner. E-mail and name are profile data and
 * are never consulted to find or link an identity.
 */
public interface FederatedIdentityStore {

    /**
     * @param email profile data, may be null
     * @param displayName profile data, may be null
     * @return the same internal id for the same (issuer, subject), always
     */
    ExternalUserId resolve(String issuer, String subject, String email, String displayName);
}
