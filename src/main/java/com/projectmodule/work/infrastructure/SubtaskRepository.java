package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Subtask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for subtasks. Archived subtasks are excluded explicitly, not by a global filter. */
public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

    Optional<Subtask> findByIdAndArchivedAtIsNull(UUID id);

    List<Subtask> findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID taskId);
}
