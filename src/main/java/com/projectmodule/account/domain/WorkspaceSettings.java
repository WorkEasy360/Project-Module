package com.projectmodule.account.domain;

import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A workspace's display settings. Its id is the workspace's organization id. */
@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings extends BaseEntity {

    public static final int NAME_MAX_LENGTH = 200;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    /** For JPA only. */
    protected WorkspaceSettings() {
    }

    private WorkspaceSettings(UUID organizationId, String name) {
        super(organizationId);
        this.name = validName(name);
    }

    public static WorkspaceSettings create(UUID organizationId, String name) {
        return new WorkspaceSettings(organizationId, name);
    }

    public void rename(String newName) {
        this.name = validName(newName);
    }

    public String getName() {
        return name;
    }

    private static String validName(String candidate) {
        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("The workspace name is required");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("The workspace name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }
}
