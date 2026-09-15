package com.projectmodule.common.exception;

import java.util.Objects;

/**
 * A single field-level validation failure, reported in the {@code errors} array of an
 * RFC 9457 problem response.
 */
public record FieldViolation(String field, String message) {

    public FieldViolation {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
