package com.projectmodule.common.exception;

/**
 * Stable catalogue of error kinds this module can report.
 *
 * <p>Each constant carries a slug used to build the {@code type} URI of an RFC 9457 response
 * and a human-readable title. HTTP status codes are deliberately absent: this enum is used by
 * the domain and application layers, which must stay free of transport concerns. The mapping
 * from error type to status lives in the web layer.
 */
public enum ErrorType {

    NOT_FOUND("not-found", "Resource not found"),
    VALIDATION_FAILED("validation-failed", "Validation failed"),
    BUSINESS_RULE_VIOLATION("business-rule-violation", "Business rule violation"),
    FORBIDDEN("forbidden", "Access denied"),
    IDENTITY_MISSING("identity-missing", "Caller identity missing"),
    CONFLICT("conflict", "Conflicting state"),
    INTEGRATION_UNAVAILABLE("integration-unavailable", "External module unavailable"),
    INTERNAL_ERROR("internal-error", "Internal server error");

    private final String slug;
    private final String title;

    ErrorType(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }
}
