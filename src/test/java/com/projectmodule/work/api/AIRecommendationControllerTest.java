package com.projectmodule.work.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.config.ApiConfiguration;
import com.projectmodule.work.application.AIRecommendationApplicationService;
import com.projectmodule.work.domain.AIRecommendation;
import com.projectmodule.work.domain.RecommendationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AIRecommendationController.class)
@Import({ApiConfiguration.class, AIRecommendationMapper.class})
class AIRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AIRecommendationApplicationService aiRecommendationApplicationService;

    @MockitoBean
    private RequestContext requestContext;

    private static AIRecommendation sample(UUID projectId) {
        return AIRecommendation.create(projectId, RecommendationType.RISK_ANALYSIS, null, null,
                "Risk summary", "Because two HIGH risks are open", null, ExternalUserId.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("POST requests a recommendation and returns 201 with a Location header")
    void requestsRecommendation() throws Exception {
        UUID projectId = UUID.randomUUID();
        AIRecommendation created = sample(projectId);
        when(aiRecommendationApplicationService.requestRecommendation(
                        eq(requestContext), eq(projectId), eq(RecommendationType.RISK_ANALYSIS), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/projects/{projectId}/ai/recommendations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RISK_ANALYSIS\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/api/v1/ai/recommendations/" + created.getId())))
                .andExpect(jsonPath("$.title").value("Risk summary"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST with a missing type is rejected")
    void rejectsMissingType() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/projects/{projectId}/ai/recommendations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST when the AI provider is unavailable returns the standard 503")
    void providerUnavailableReturns503() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(aiRecommendationApplicationService.requestRecommendation(
                        eq(requestContext), eq(projectId), eq(RecommendationType.RISK_ANALYSIS), any(), any()))
                .thenThrow(new IntegrationUnavailableException("AI provider", null));

        mockMvc.perform(post("/api/v1/projects/{projectId}/ai/recommendations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RISK_ANALYSIS\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/integration-unavailable"));
    }

    @Test
    @DisplayName("GET by id returns the recommendation")
    void getsRecommendation() throws Exception {
        AIRecommendation recommendation = sample(UUID.randomUUID());
        when(aiRecommendationApplicationService.getRecommendation(requestContext, recommendation.getId()))
                .thenReturn(recommendation);

        mockMvc.perform(get("/api/v1/ai/recommendations/{id}", recommendation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Risk summary"));
    }

    @Test
    @DisplayName("GET lists recommendations for the project")
    void listsRecommendations() throws Exception {
        UUID projectId = UUID.randomUUID();
        AIRecommendation recommendation = sample(projectId);
        when(aiRecommendationApplicationService.listRecommendations(requestContext, projectId))
                .thenReturn(List.of(recommendation));

        mockMvc.perform(get("/api/v1/projects/{projectId}/ai/recommendations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Risk summary"));
    }

    @Test
    @DisplayName("POST accept transitions the recommendation")
    void acceptsRecommendation() throws Exception {
        AIRecommendation recommendation = sample(UUID.randomUUID());
        recommendation.accept(ExternalUserId.of(UUID.randomUUID()));
        when(aiRecommendationApplicationService.acceptRecommendation(requestContext, recommendation.getId()))
                .thenReturn(recommendation);

        mockMvc.perform(post("/api/v1/ai/recommendations/{id}/accept", recommendation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST reject transitions the recommendation")
    void rejectsRecommendation() throws Exception {
        AIRecommendation recommendation = sample(UUID.randomUUID());
        recommendation.reject(ExternalUserId.of(UUID.randomUUID()));
        when(aiRecommendationApplicationService.rejectRecommendation(requestContext, recommendation.getId()))
                .thenReturn(recommendation);

        mockMvc.perform(post("/api/v1/ai/recommendations/{id}/reject", recommendation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
