package com.projectmodule.attachment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** The bytes of an {@link Attachment}. Inserted once and never changed. */
@Entity
@Table(name = "attachment_contents")
public class AttachmentContent implements Persistable<UUID> {

    @Id
    @Column(name = "attachment_id", nullable = false, updatable = false)
    private UUID attachmentId;

    @Column(name = "data", nullable = false, updatable = false)
    private byte[] data;

    @Transient
    private boolean isNew = true;

    /** For JPA only. */
    protected AttachmentContent() {
    }

    public AttachmentContent(UUID attachmentId, byte[] data) {
        this.attachmentId = attachmentId;
        this.data = data;
    }

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
        return attachmentId;
    }

    public byte[] getData() {
        return data;
    }
}
