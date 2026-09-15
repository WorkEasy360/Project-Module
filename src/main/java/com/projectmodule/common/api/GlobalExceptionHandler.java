package com.projectmodule.common.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ErrorType;
import com.projectmodule.common.exception.FieldViolation;
import com.projectmodule.common.exception.ProjectModuleException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into RFC 9457 problem responses.
 *
 * <p>Every error leaves this module in the same shape, carrying the correlation identifier so
 * a response can be tied to its log entries. Stack traces, SQL and exception class names are
 * never written to a response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://errors.projectmodule/";

    private final RequestContext requestContext;

    public GlobalExceptionHandler(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    /** Handles every error this module raises deliberately. */
    @ExceptionHandler(ProjectModuleException.class)
    public ProblemDetail handleProjectModuleException(ProjectModuleException exception) {
        ErrorType errorType = exception.errorType();
        HttpStatus status = statusFor(errorType);

        if (status.is5xxServerError()) {
            log.error("{} [{}]", errorType.slug(), correlationId(), exception);
        } else {
            log.warn("{} [{}]: {}", errorType.slug(), correlationId(), exception.getMessage());
        }

        return problem(status, errorType, exception.getMessage(), exception.violations());
    }

    /** Handles Bean Validation failures on request payloads. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();

        log.warn("validation-failed [{}]: {} field error(s)", correlationId(), violations.size());

        return problem(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "One or more fields are invalid", violations);
    }

    /** Last resort. The cause is logged in full and withheld from the response. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("internal-error [{}]", correlationId(), exception);

        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_ERROR,
                "An unexpected error occurred", List.of());
    }

    /**
     * Maps an error type to a status.
     *
     * <p>The switch is exhaustive over the enum, so a new error type will not compile until it
     * has been given a status here.
     */
    private static HttpStatus statusFor(ErrorType errorType) {
        return switch (errorType) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case BUSINESS_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case IDENTITY_MISSING -> HttpStatus.UNAUTHORIZED;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ProblemDetail problem(HttpStatus status,
                                  ErrorType errorType,
                                  String detail,
                                  List<FieldViolation> violations) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(TYPE_BASE + errorType.slug()));
        problemDetail.setTitle(errorType.title());
        problemDetail.setProperty("traceId", correlationId());
        if (!violations.isEmpty()) {
            problemDetail.setProperty("errors", violations);
        }
        return problemDetail;
    }

    private String correlationId() {
        return requestContext.correlationId();
    }
}
