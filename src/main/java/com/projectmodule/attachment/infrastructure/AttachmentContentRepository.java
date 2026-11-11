package com.projectmodule.attachment.infrastructure;

import com.projectmodule.attachment.domain.AttachmentContent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentContentRepository extends JpaRepository<AttachmentContent, UUID> {
}
