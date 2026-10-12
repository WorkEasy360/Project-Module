/**
 * "Sign in with Workeasy360": authentication, the server-side session, and the entitlement check
 * that keeps asking Workeasy360 whether a signed-in person's organization may still use Easy Project.
 *
 * <p>The protocol is Spring Security's OAuth2/OIDC client, customised to the Workeasy360 product
 * contract (docs/workeasy360-sso.md). Nothing in the rest of the application knows about it:
 * downstream code reads {@code RequestContext}, which is derived from the authenticated principal
 * here and from nothing else.
 */
package com.projectmodule.security;
