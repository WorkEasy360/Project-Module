/**
 * Email and password accounts, and the sessions that sign-in creates.
 *
 * <p>This is the one place credentials are handled. Everything else in the module still sees only
 * a {@link com.projectmodule.common.context.RequestContext}, so no other package knows or cares how
 * the caller was authenticated, and an upstream gateway can replace this package without touching
 * them.
 */
package com.projectmodule.account;
