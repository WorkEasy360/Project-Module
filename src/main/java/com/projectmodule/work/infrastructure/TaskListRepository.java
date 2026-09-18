package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.TaskList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for task lists. Archived task lists are excluded explicitly, not by a global filter. */
public interface TaskListRepository extends JpaRepository<TaskList, UUID> {

    Optional<TaskList> findByIdAndArchivedAtIsNull(UUID id);

    List<TaskList> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);
}
