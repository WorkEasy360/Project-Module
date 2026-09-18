package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Something that happened to a {@link com.projectmodule.work.domain.Risk}.
 *
 * <p>Sealed to exactly the three Risk events in {@code docs/project/05-EVENTS.md}.
 */
public sealed interface RiskDomainEvent permits RiskCreated, RiskUpdated, RiskResolved {

    UUID riskId();
}
