package com.projectmodule.common.exception;

/**
 * The request carried no caller identity where one is required.
 *
 * <p>The security chain refuses unauthenticated requests to the API before they reach a controller,
 * so this is a backstop for code paths that ask for an identity where none was established.
 */
public final class MissingIdentityException extends ProjectModuleException {

    public MissingIdentityException(String message) {
        super(ErrorType.IDENTITY_MISSING, message);
    }
}
