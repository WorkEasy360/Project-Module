package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoopDocumentAdapterTest {

    private final NoopDocumentAdapter adapter = new NoopDocumentAdapter();

    @Test
    @DisplayName("returns empty rather than a fabricated document")
    void returnsEmpty() {
        assertThat(adapter.findById("doc-123")).isEmpty();
    }
}
