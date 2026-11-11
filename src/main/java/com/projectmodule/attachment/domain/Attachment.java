package com.projectmodule.attachment.domain;

import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A file on a task. Its bytes live in {@link AttachmentContent}, so listing files stays cheap. */
@Entity
@Table(name = "attachments")
public class Attachment extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 255;
    private static final int CONTENT_TYPE_MAX_LENGTH = 150;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "file_name", nullable = false, updatable = false, length = NAME_MAX_LENGTH)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = CONTENT_TYPE_MAX_LENGTH)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    /** For JPA only. */
    protected Attachment() {
    }

    private Attachment(UUID id, UUID taskId, UUID projectId, String fileName, String contentType, long sizeBytes,
                       UUID uploadedBy) {
        super(id);
        this.taskId = taskId;
        this.projectId = projectId;
        this.fileName = cleanFileName(fileName);
        this.contentType = contentType == null || contentType.isBlank() || contentType.length() > CONTENT_TYPE_MAX_LENGTH
                ? "application/octet-stream" : contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    public static Attachment create(UUID taskId, UUID projectId, String fileName, String contentType, long sizeBytes,
                                    UUID uploadedBy) {
        return new Attachment(UuidV7.generate(), taskId, projectId, fileName, contentType, sizeBytes, uploadedBy);
    }

    /** Keeps only the file's own name: a browser may send a path, and control characters are unsafe in headers. */
    static String cleanFileName(String original) {
        if (original == null) {
            throw new ValidationException("A file name is required");
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .trim();
        if (name.isEmpty()) {
            throw new ValidationException("A file name is required");
        }
        return name.length() > NAME_MAX_LENGTH ? name.substring(name.length() - NAME_MAX_LENGTH) : name;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }
}
