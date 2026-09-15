package com.projectmodule.common.exception;

/**
 * An external module could not be reached.
 *
 * <p>Mapped to a distinct status so callers can tell a failure of this module from a failure
 * of one of its dependencies.
 */
public final class IntegrationUnavailableException extends ProjectModuleException {

    public IntegrationUnavailableException(String module, Throwable cause) {
        super(ErrorType.INTEGRATION_UNAVAILABLE,
                "External module unavailable: " + module,
                java.util.List.of(),
                cause);
    }
}
