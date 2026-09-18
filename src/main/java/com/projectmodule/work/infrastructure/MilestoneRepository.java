package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for milestones. Archived milestones are excluded explicitly, not by a global filter. */
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    Optional<Milestone> findByIdAndArchivedAtIsNull(UUID id);

    List<Milestone> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /** For {@code docs/project/17-CALENDAR-SPEC.md} §5: active milestones with a real due date. */
    List<Milestone> findByProjectIdAndDueDateIsNotNullAndArchivedAtIsNullOrderByDueDateAsc(UUID projectId);

    /**
     * For {@code docs/project/22-DELAY-DETECTION-SPEC.md} §5: active, not-yet-completed
     * milestones whose due date has already passed.
     */
    List<Milestone> findByProjectIdAndDueDateBeforeAndStatusNotAndArchivedAtIsNullOrderByDueDateAsc(
            UUID projectId, LocalDate today, MilestoneStatus excludedStatus);
}
