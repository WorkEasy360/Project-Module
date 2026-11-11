package com.projectmodule.work.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** Someone who is told when a task gets a comment or is completed. */
@Entity
@Table(name = "task_followers")
@IdClass(TaskFollower.Key.class)
public class TaskFollower {

    public record Key(UUID taskId, UUID userId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** For JPA only. */
    protected TaskFollower() {
    }

    public TaskFollower(UUID taskId, UUID userId) {
        this.taskId = taskId;
        this.userId = userId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getUserId() {
        return userId;
    }
}
