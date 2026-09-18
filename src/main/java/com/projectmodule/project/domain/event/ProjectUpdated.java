package com.projectmodule.project.domain.event;

import java.util.UUID;

/**
 * Raised once by {@code Project.recordUpdated()}. Corresponds to {@code project.updated}.
 *
 * <p>Not raised by the individual field mutators ({@code rename}, {@code describe},
 * {@code changeStatus}, {@code changePriority}, {@code schedule}): a single {@code PATCH}
 * request may call several of them, and one event per mutator would produce several audit and
 * outbox records for one logical update. The application layer calls {@code recordUpdated()}
 * exactly once, after applying every field change a request asked for.
 */
public record ProjectUpdated(UUID projectId) implements ProjectDomainEvent {
}
