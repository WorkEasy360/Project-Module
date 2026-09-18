package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.integration.port.AIProviderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnavailableAIProviderAdapterTest {

    private final UnavailableAIProviderAdapter adapter = new UnavailableAIProviderAdapter();

    @Test
    @DisplayName("always throws IntegrationUnavailableException rather than returning fabricated content")
    void alwaysThrows() {
        AIProviderRequest request = new AIProviderRequest("RISK_ANALYSIS", "some context", null);

        assertThatThrownBy(() -> adapter.generate(request))
                .isInstanceOf(IntegrationUnavailableException.class);
    }
}
