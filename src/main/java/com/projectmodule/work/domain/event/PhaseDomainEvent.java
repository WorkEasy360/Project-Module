package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.work.domain.Phase}.
 *
 * <p>Raised by the aggregate as its behaviour methods run, held in memory until the application
 * layer collects them and turns each into a {@code ProjectActivity} audit entry and an
 * {@code OutboxMessage}, in the same transaction as the state change — the same pattern
 * {@code ProjectDomainEvent} already establishes for {@code Project}. Sealed to exactly the two
 * Phase events in {@code docs/project/05-EVENTS.md}.
 */
public sealed interface PhaseDomainEvent permits PhaseCreated, PhaseUpdated {

    UUID phaseId();
}
