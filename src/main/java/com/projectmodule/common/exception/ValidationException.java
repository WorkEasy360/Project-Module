package com.projectmodule.common.exception;

import java.util.List;

/**
 * Input was malformed or failed a contextual check.
 *
 * <p>Raised from the application layer as well as the web layer, so that input arriving
 * through a non-HTTP path is validated identically.
 */
public final class ValidationException extends ProjectModuleException {

    public ValidationException(String message) {
        super(ErrorType.VALIDATION_FAILED, message);
    }

    public ValidationException(String message, List<FieldViolation> violations) {
        super(ErrorType.VALIDATION_FAILED, message, violations, null);
    }
}
