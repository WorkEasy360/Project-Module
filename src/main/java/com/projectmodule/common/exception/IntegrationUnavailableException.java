package com.projectmodule.common.exception;

/**
 * An external module could not be reached.
 *
 * <p>Mapped to a distinct status so callers can tell a failure of this module from a failure
 * of one of its dependencies.
 */
public final class IntegrationUnavailableException extends ProjectModuleException {

    public IntegrationUnavailableException(String module, Throwable cause) {
        this("External module unavailable: " + module, cause, true);
    }

    private IntegrationUnavailableException(String message, Throwable cause, boolean ignored) {
        super(ErrorType.INTEGRATION_UNAVAILABLE, message, java.util.List.of(), cause);
    }

    /** For a message written to be shown to a person as it is. */
    public static IntegrationUnavailableException withMessage(String message, Throwable cause) {
        return new IntegrationUnavailableException(message, cause, true);
    }
}
