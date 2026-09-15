package com.projectmodule.common.exception;

/** A resource owned by this module was requested but does not exist. */
public final class ResourceNotFoundException extends ProjectModuleException {

    public ResourceNotFoundException(String message) {
        super(ErrorType.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(ErrorType.NOT_FOUND, resourceType + " " + identifier + " was not found");
    }
}
