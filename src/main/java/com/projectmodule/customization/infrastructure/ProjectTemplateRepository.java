package com.projectmodule.customization.infrastructure;

import com.projectmodule.customization.domain.ProjectTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for project templates. Archived templates are excluded explicitly, not by a
 * global filter. Active-name uniqueness per organization is enforced at the database level by a
 * partial unique index (see {@code V10__create_project_templates.sql}), not here.
 *
 * <p>Listing is paginated, mirroring {@code ProjectRepository.findByOrganizationIdAndArchivedAtIsNull}
 * rather than the plain unbounded lists the {@code work} sub-entities use — {@code ProjectTemplate}
 * sits at {@code Project}'s own organization-scoped level, not below it as a project sub-entity,
 * the same reasoning {@code docs/project/12-TEMPLATE-SPEC.md} §7 gives for including a
 * single-item {@code GET}.
 */
public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, UUID> {

    Optional<ProjectTemplate> findByIdAndArchivedAtIsNull(UUID id);

    Page<ProjectTemplate> findByOrganizationIdAndArchivedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByOrganizationIdAndNameAndArchivedAtIsNull(UUID organizationId, String name);
}
