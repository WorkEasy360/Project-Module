package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Raised by {@code Dependency.markBroken()}. Corresponds to {@code dependency.broken}.
 *
 * <p>Set explicitly by a person through {@code PATCH}, not computed automatically from task
 * state — there is no dependency-driven automation in this slice.
 */
public record DependencyBroken(UUID dependencyId) implements DependencyDomainEvent {
}
