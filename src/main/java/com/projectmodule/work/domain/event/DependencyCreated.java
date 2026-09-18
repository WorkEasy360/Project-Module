package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Dependency.create(...)}. Corresponds to {@code dependency.created}. */
public record DependencyCreated(UUID dependencyId) implements DependencyDomainEvent {
}
