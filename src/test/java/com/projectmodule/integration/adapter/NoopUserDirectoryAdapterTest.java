package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoopUserDirectoryAdapterTest {

    private final NoopUserDirectoryAdapter adapter = new NoopUserDirectoryAdapter();

    @Test
    @DisplayName("returns empty rather than a fabricated user")
    void returnsEmpty() {
        assertThat(adapter.findById(ExternalUserId.of(UUID.randomUUID()))).isEmpty();
    }
}
