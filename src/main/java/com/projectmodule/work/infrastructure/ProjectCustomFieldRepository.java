package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.ProjectCustomField;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for project custom fields. Archived fields are excluded explicitly, not by a
 * global filter. Active-name uniqueness per project is enforced at the database level by a
 * partial unique index (see {@code V9__create_project_custom_fields.sql}), not here.
 */
public interface ProjectCustomFieldRepository extends JpaRepository<ProjectCustomField, UUID> {

    Optional<ProjectCustomField> findByIdAndArchivedAtIsNull(UUID id);

    List<ProjectCustomField> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    boolean existsByProjectIdAndNameAndArchivedAtIsNull(UUID projectId, String name);
}
