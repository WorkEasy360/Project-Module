package com.projectmodule.common.exception;

/**
 * The request carried no valid caller identity where one is required, or sign-in was refused.
 * Either way the caller is not authenticated, so both are answered 401.
 *
 * <p>The security chain refuses unauthenticated requests to the API before they reach a controller,
 * so for a missing identity this is a backstop for code paths that ask for one where none was
 * established.
 */
public final class MissingIdentityException extends ProjectModuleException {

    public MissingIdentityException(String message) {
        super(ErrorType.IDENTITY_MISSING, message);
    }
}
