package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.projectmodule.common.identity.OrganizationId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingChatAdapterTest {

    private final LoggingChatAdapter adapter = new LoggingChatAdapter();

    @Test
    @DisplayName("accepts a post-message request without throwing, posting nothing")
    void doesNotThrow() {
        assertThatCode(() -> adapter.postMessage(
                OrganizationId.of(UUID.randomUUID()), "channel-123", "Apollo was archived"))
                .doesNotThrowAnyException();
    }
}
