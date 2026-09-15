package com.projectmodule.common.exception;

import java.util.List;
import java.util.Objects;

/**
 * Base type for every error this module raises deliberately.
 *
 * <p>The hierarchy is sealed so the set of failure modes is closed and known at compile time.
 * The web layer can switch over {@link ErrorType} exhaustively, and adding a new failure mode
 * forces the mapping to be updated rather than silently falling through to a 500.
 */
public abstract sealed class ProjectModuleException extends RuntimeException
        permits ResourceNotFoundException,
                ValidationException,
                BusinessRuleViolationException,
                AuthorizationException,
                MissingIdentityException,
                ConflictException,
                IntegrationUnavailableException {

    private final transient ErrorType errorType;
    private final transient List<FieldViolation> violations;

    protected ProjectModuleException(ErrorType errorType, String message) {
        this(errorType, message, List.of(), null);
    }

    protected ProjectModuleException(ErrorType errorType,
                                     String message,
                                     List<FieldViolation> violations,
                                     Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
        this.violations = List.copyOf(violations);
    }

    public ErrorType errorType() {
        return errorType;
    }

    /** Field-level detail, empty for errors that are not field specific. */
    public List<FieldViolation> violations() {
        return violations;
    }
}
