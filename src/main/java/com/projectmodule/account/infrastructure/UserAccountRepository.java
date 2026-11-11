package com.projectmodule.account.infrastructure;

import com.projectmodule.account.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByOrganizationIdAndWorkspaceRole(UUID organizationId, com.projectmodule.account.domain.WorkspaceRole role);

    List<UserAccount> findByOrganizationIdOrderByDisplayNameAsc(UUID organizationId);
}
