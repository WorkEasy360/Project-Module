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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * Handles a query/path parameter that cannot be converted to its declared type — most
     * commonly an invalid enum value in a Filters query parameter (e.g. {@code ?status=NOT_A_STATUS}),
     * per {@code docs/project/14-FILTERS-SPEC.md} §14. Without this handler, Spring's own default
     * 400 response would leak through in Spring's generic shape rather than this module's
     * {@code ProblemDetail} — the same {@link ErrorType#VALIDATION_FAILED} shape
     * {@link #handleMethodArgumentNotValid} already reports for an invalid request body field.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String requiredType = exception.getRequiredType() == null
                ? "the expected type" : exception.getRequiredType().getSimpleName();
        FieldViolation violation = new FieldViolation(
                exception.getName(), "must be a valid " + requiredType);

        log.warn("validation-failed [{}]: invalid value for parameter '{}'", correlationId(), exception.getName());

        return problem(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "One or more fields are invalid", List.of(violation));
    }

    /**
     * Handles a request body that cannot be read into its declared type — malformed JSON, an
     * enum value that does not exist (e.g. {@code "status": "IN_PROGRESS"}) or an unparseable
     * date (e.g. {@code "dueDate": "2026-13-45"}).
     *
     * <p>Without this handler such a request fell through to {@link #handleUnexpected} and was
     * reported as a 500 although the fault is entirely the caller's. It is the body-side
     * counterpart of {@link #handleMethodArgumentTypeMismatch}: same
     * {@link ErrorType#VALIDATION_FAILED} shape as every other invalid input. Jackson's message
     * (which can echo the offending value and internal class names) is deliberately not copied
     * into the response.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("validation-failed [{}]: request body could not be read", correlationId());

        return problem(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "Request body is malformed or contains an invalid value", List.of());
    }

    /**
     * Handles a genuine optimistic-lock failure detected by Hibernate's own version check at
     * flush time.
     *
     * <p>The primary conflict check is the application-layer comparison against the client's
     * supplied version, which raises {@link com.projectmodule.common.exception.ConflictException}
     * and is handled above. This is the narrower, secondary case: two requests both pass that
     * check because they read the same version before either committed. It is the same kind of
     * conflict and is reported the same way.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        log.warn("conflict [{}]: concurrent modification detected", correlationId());

        return problem(HttpStatus.CONFLICT, ErrorType.CONFLICT,
                "The resource was modified concurrently; reload and try again", List.of());
    }

    /**
     * Handles a request for a path this module does not map.
     *
     * <p>Spring reports an unmapped path by raising {@link NoResourceFoundException} (or
     * {@link NoHandlerFoundException}). Without this handler those exceptions reach
     * {@link #handleUnexpected} — the catch-all below — and a plain "this URL does not exist"
     * is reported as a 500 Internal Server Error. That is wrong twice over: it tells a caller
     * the server is broken when it is healthy, and a deployment health check that probes
     * {@code /} sees a 5xx and marks a working environment unhealthy. A missing route is a
     * caller error, so it is answered as a 404 in the same shape as every other error here.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleNoHandlerFound(Exception exception) {
        log.warn("not-found [{}]: no endpoint is mapped to the requested path", correlationId());

        return problem(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND,
                "No endpoint exists at this path", List.of());
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
