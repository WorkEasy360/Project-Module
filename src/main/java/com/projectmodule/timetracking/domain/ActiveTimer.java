package com.projectmodule.timetracking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A person's running timer. Kept on the server so it survives closing the browser. */
@Entity
@Table(name = "active_timers")
public class ActiveTimer implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Transient
    private boolean isNew = true;

    /** For JPA only. */
    protected ActiveTimer() {
    }

    public ActiveTimer(UUID userId, UUID taskId, Instant startedAt) {
        this.userId = userId;
        this.taskId = taskId;
        this.startedAt = startedAt;
    }

    /** Spring Data skips deleting anything it believes is new, so this must become false once saved. */
    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
