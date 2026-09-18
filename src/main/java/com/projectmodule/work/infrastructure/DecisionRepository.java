package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.Decision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for decisions. Archived decisions are excluded explicitly, not by a global filter. */
public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByIdAndArchivedAtIsNull(UUID id);

    List<Decision> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /** For {@code docs/project/24-REPORTS-SPEC.md} §4: the project summary report's decision count. */
    long countByProjectIdAndArchivedAtIsNull(UUID projectId);

    /**
     * Active decisions in the given project whose {@code name} or {@code description} contains
     * {@code term}, case-insensitively. See {@code TaskRepository.search} for why this is
     * {@code @Query} rather than a derived method name. Unpaginated — see
     * {@code docs/project/13-SEARCH-SPEC.md} §4.
     */
    @Query("SELECT d FROM Decision d WHERE d.projectId = :projectId AND d.archivedAt IS NULL "
            + "AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :term, '%')) "
            + "OR LOWER(d.description) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Decision> search(@Param("projectId") UUID projectId, @Param("term") String term);

    /**
     * Decisions in the given project matching every supplied filter (AND), per
     * {@code docs/project/14-FILTERS-SPEC.md}. See {@code TaskRepository.findByFilters} for why
     * this uses the {@code (:param IS NULL OR field = :param)} form and how {@code archived} is
     * a strict either/or, not a combined view. Takes a trailing {@link Sort} instead of a
     * hardcoded {@code ORDER BY} — see {@code docs/project/15-LIST-SPEC.md}.
     */
    @Query("SELECT d FROM Decision d WHERE d.projectId = :projectId "
            + "AND ((:archived = true AND d.archivedAt IS NOT NULL) OR (:archived = false AND d.archivedAt IS NULL)) "
            + "AND (:decidedBy IS NULL OR d.decidedBy = :decidedBy)")
    List<Decision> findByFilters(@Param("projectId") UUID projectId,
                                 @Param("decidedBy") UUID decidedBy,
                                 @Param("archived") boolean archived,
                                 Sort sort);
}
