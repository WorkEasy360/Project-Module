package com.projectmodule.common.identity;

import com.projectmodule.common.exception.ValidationException;
import java.util.Objects;
import java.util.UUID;

/**
 * Reference to an organization owned by the external Organization Management module.
 *
 * <p>Held as an opaque identifier for tenant scoping. This module never stores organization
 * attributes and never joins to an organization table.
 */
public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "organization id must not be null");
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    /**
     * Parses an identifier supplied by a caller.
     *
     * @throws ValidationException if the value is not a well-formed UUID
     */
    public static OrganizationId fromString(String value) {
        try {
            return new OrganizationId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Malformed organization identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
