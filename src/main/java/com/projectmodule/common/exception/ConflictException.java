package com.projectmodule.common.exception;

/** The request conflicts with current state, such as a stale version or a duplicate. */
public final class ConflictException extends ProjectModuleException {

    public ConflictException(String message) {
        super(ErrorType.CONFLICT, message);
    }
}
