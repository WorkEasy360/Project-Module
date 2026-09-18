package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Checklist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for checklist lines. Archived lines are excluded explicitly, not by a global filter. */
public interface ChecklistRepository extends JpaRepository<Checklist, UUID> {

    Optional<Checklist> findByIdAndArchivedAtIsNull(UUID id);

    List<Checklist> findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID taskId);
}
