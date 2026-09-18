package com.projectmodule.project.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.project.domain.Project}.
 *
 * <p>Raised by the aggregate itself as its behaviour methods run, held in memory until the
 * application layer collects them (see {@code Project.pullDomainEvents()}) and turns each one
 * into a {@code ProjectActivity} audit entry and an {@code OutboxMessage}, in the same
 * transaction as the state change. This is deliberately not a general event framework: it is
 * three closed cases, matching the three Project events in {@code docs/project/05-EVENTS.md}.
 *
 * <p>Sealed so the mapping from event to event-type string, activity type and payload (in
 * {@code ProjectEventRecorder}) is exhaustive and a new event cannot be added without every one
 * of those mappings being updated to handle it.
 */
public sealed interface ProjectDomainEvent permits ProjectCreated, ProjectUpdated, ProjectArchived {

    /** The project this event concerns; the outbox and audit aggregate identifier. */
    UUID projectId();
}
