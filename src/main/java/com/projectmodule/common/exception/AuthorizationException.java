package com.projectmodule.common.exception;

/**
 * The caller is known but lacks the required permission on the target project.
 *
 * <p>This concerns project-level permission, which this module owns. It is not an
 * authentication failure; authentication belongs to another module.
 */
public final class AuthorizationException extends ProjectModuleException {

    public AuthorizationException(String message) {
        super(ErrorType.FORBIDDEN, message);
    }
}
