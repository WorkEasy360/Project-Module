package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Phase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for phases. Archived phases are excluded explicitly, not by a global filter. */
public interface PhaseRepository extends JpaRepository<Phase, UUID> {

    Optional<Phase> findByIdAndArchivedAtIsNull(UUID id);

    List<Phase> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /**
     * For {@code docs/project/17-CALENDAR-SPEC.md} §5: active phases with a real start and end
     * date.
     */
    List<Phase> findByProjectIdAndStartDateIsNotNullAndEndDateIsNotNullAndArchivedAtIsNullOrderByStartDateAsc(
            UUID projectId);

    /**
     * For {@code docs/project/22-DELAY-DETECTION-SPEC.md} §5: active phases whose end date has
     * already passed. Phase has no status/completion field, so unlike Task/Milestone there is no
     * "not yet completed" condition to add — see the spec's explicit note on this limitation.
     */
    List<Phase> findByProjectIdAndEndDateBeforeAndArchivedAtIsNullOrderByEndDateAsc(UUID projectId, LocalDate today);
}
