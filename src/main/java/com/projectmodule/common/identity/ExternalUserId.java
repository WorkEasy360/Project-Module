package com.projectmodule.common.identity;

import com.projectmodule.common.exception.ValidationException;
import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a user owned by the external User Management module.
 *
 * <p>This module stores the identifier and nothing else. There is no {@code users} table here
 * and no foreign key to one, because the user record belongs to another module. Wrapping the
 * raw {@link UUID} in a dedicated type keeps that boundary visible in every signature and
 * makes it impossible to pass a project identifier where a user identifier is expected.
 */
public record ExternalUserId(UUID value) {

    public ExternalUserId {
        Objects.requireNonNull(value, "external user id must not be null");
    }

    public static ExternalUserId of(UUID value) {
        return new ExternalUserId(value);
    }

    /**
     * Parses an identifier supplied by a caller.
     *
     * @throws ValidationException if the value is not a well-formed UUID
     */
    public static ExternalUserId fromString(String value) {
        try {
            return new ExternalUserId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Malformed user identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
