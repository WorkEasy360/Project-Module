package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingNotificationAdapterTest {

    private final LoggingNotificationAdapter adapter = new LoggingNotificationAdapter();

    @Test
    @DisplayName("accepts a notification request without throwing, delivering nothing")
    void doesNotThrow() {
        assertThatCode(() -> adapter.notify(
                OrganizationId.of(UUID.randomUUID()),
                ExternalUserId.of(UUID.randomUUID()),
                "Project archived",
                "Apollo was archived"))
                .doesNotThrowAnyException();
    }
}
