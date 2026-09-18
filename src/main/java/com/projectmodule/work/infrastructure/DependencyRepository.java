package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Dependency;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for dependencies. Archived dependencies are excluded explicitly, not by a global
 * filter.
 */
public interface DependencyRepository extends JpaRepository<Dependency, UUID> {

    Optional<Dependency> findByIdAndArchivedAtIsNull(UUID id);

    List<Dependency> findByDependentTaskIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID dependentTaskId);

    /**
     * For {@code docs/project/23-DEPENDENCY-ANALYSIS-SPEC.md} §4: every active dependency
     * touching any of this project's tasks. {@code Dependency} has no {@code projectId} column of
     * its own, so scoping to "this project's dependencies" means resolving the project's active
     * task ids first, then querying by that set — the same indirection
     * {@code DependencyApplicationService}'s own authorization check already uses.
     */
    List<Dependency> findByDependentTaskIdInAndArchivedAtIsNull(Collection<UUID> dependentTaskIds);

    /**
     * Whether an active dependency already exists for this exact ordered pair. Checked in both
     * directions by the application service: once for an exact duplicate, once with the
     * arguments swapped to catch a direct reverse (a two-task cycle).
     */
    boolean existsByDependentTaskIdAndPrerequisiteTaskIdAndArchivedAtIsNull(
            UUID dependentTaskId, UUID prerequisiteTaskId);
}
