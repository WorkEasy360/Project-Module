package com.projectmodule.security.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link FederatedIdentity}. */
public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentity, UUID> {

    Optional<FederatedIdentity> findByIssuerAndSubject(String issuer, String subject);

    /** Whether the user id belongs to any identity, of any issuer. */
    boolean existsByUserId(UUID userId);
}
