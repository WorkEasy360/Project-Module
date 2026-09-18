package com.projectmodule.events.infrastructure;

import com.projectmodule.events.domain.OutboxMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for the transactional outbox. */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * The publisher query: oldest unpublished events first, so ordering follows the order in
     * which the changes actually happened. Backed by a partial index that ignores published
     * rows entirely.
     */
    List<OutboxMessage> findByPublishedAtIsNullOrderByOccurredAtAsc(Pageable pageable);

    long countByPublishedAtIsNull();

    List<OutboxMessage> findByAggregateIdOrderByOccurredAtAsc(UUID aggregateId);
}
