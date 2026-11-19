package com.projectmodule.account.infrastructure;

import com.projectmodule.account.domain.WorkspaceSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {
}
