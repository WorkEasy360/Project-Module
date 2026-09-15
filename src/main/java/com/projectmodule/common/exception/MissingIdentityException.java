package com.projectmodule.common.exception;

/**
 * The request carried no caller identity where one is required.
 *
 * <p>This module does not authenticate anyone. Identity is expected to have been established
 * upstream and passed in. This exception reports that it was absent, which is a contract
 * failure between the gateway and this module rather than a rejected credential.
 */
public final class MissingIdentityException extends ProjectModuleException {

    public MissingIdentityException(String message) {
        super(ErrorType.IDENTITY_MISSING, message);
    }
}
