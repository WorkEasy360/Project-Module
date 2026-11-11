package com.projectmodule.assistant.infrastructure;

import com.projectmodule.assistant.domain.AssistantConversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {
}
