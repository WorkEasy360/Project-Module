package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Raised once by {@code Risk.resolve()}. Corresponds to {@code risk.resolved}. There is no
 * {@code risk.reopened} event, so there is no reverse transition out of
 * {@link com.projectmodule.work.domain.RiskStatus#RESOLVED} in this slice.
 */
public record RiskResolved(UUID riskId) implements RiskDomainEvent {
}
