package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.ProjectComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for project comments. Archived comments are excluded explicitly, not by a global filter. */
public interface ProjectCommentRepository extends JpaRepository<ProjectComment, UUID> {

    Optional<ProjectComment> findByIdAndArchivedAtIsNull(UUID id);

    List<ProjectComment> findByTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID taskId);
}
