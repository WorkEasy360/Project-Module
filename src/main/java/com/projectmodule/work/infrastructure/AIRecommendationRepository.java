package com.projectmodule.work.infrastructure;

import com.projectmodule.work.domain.AIRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for AI recommendations. No archive filter: there is no soft-archive (see
 * {@link AIRecommendation}). Lookup by plain id (via the inherited {@code findById}) is
 * sufficient for the accept/reject endpoints — the same "flat item, look up its own project for
 * authorization" convention {@code TaskApplicationService.findActiveTask} already establishes.
 */
public interface AIRecommendationRepository extends JpaRepository<AIRecommendation, UUID> {

    List<AIRecommendation> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
