package com.projectmodule.common.exception;

/**
 * The request carried no valid caller identity where one is required, or sign-in was refused.
 * Either way the caller is not authenticated, so both are answered 401.
 */
public final class MissingIdentityException extends ProjectModuleException {

    public MissingIdentityException(String message) {
        super(ErrorType.IDENTITY_MISSING, message);
    }
}
