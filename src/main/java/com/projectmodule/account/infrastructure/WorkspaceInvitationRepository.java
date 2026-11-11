package com.projectmodule.account.infrastructure;

import com.projectmodule.account.domain.WorkspaceInvitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    @Query("""
            select i from WorkspaceInvitation i
            where i.organizationId = :organizationId and i.acceptedAt is null and i.revokedAt is null
              and i.expiresAt > :now
            order by i.createdAt desc""")
    List<WorkspaceInvitation> findPending(@Param("organizationId") UUID organizationId, @Param("now") Instant now);
}
