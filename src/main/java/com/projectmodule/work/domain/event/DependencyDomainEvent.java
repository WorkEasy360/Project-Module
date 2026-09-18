package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.work.domain.Dependency}.
 *
 * <p>Sealed to exactly the two Dependency events in {@code docs/project/05-EVENTS.md}.
 */
public sealed interface DependencyDomainEvent permits DependencyCreated, DependencyBroken {

    UUID dependencyId();
}
