package com.projectmodule.attachment.infrastructure;

import com.projectmodule.attachment.domain.Attachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
