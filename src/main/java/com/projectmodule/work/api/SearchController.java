package com.projectmodule.work.api;

import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.SearchResultResponse;
import com.projectmodule.work.application.SearchApplicationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for project search, per {@code docs/project/13-SEARCH-SPEC.md} (approved).
 * Project-scoped only — no organization-wide search (approved Decision #1). Holds no business
 * logic or authorization decision; both are delegated to {@link SearchApplicationService}. No
 * mapper: the application service already returns the API-facing {@link SearchResultResponse}
 * directly, since Search has no single domain aggregate of its own to map from.
 */
@RestController
public class SearchController {

    private final SearchApplicationService searchApplicationService;
    private final RequestContext requestContext;

    public SearchController(SearchApplicationService searchApplicationService, RequestContext requestContext) {
        this.searchApplicationService = searchApplicationService;
        this.requestContext = requestContext;
    }

    /**
     * {@code q} is deliberately {@code required = false}: an entirely omitted {@code q} and a
     * present-but-blank {@code q} (e.g. {@code ?q=}) both reach
     * {@link SearchApplicationService#search}'s own blank check, so both are reported the same
     * way — {@code BusinessRuleViolationException} / HTTP 422 — rather than the omitted case
     * instead producing Spring's generic 400 for a missing required parameter.
     */
    @GetMapping("/projects/{projectId}/search")
    public PageResponse<SearchResultResponse> search(
            @PathVariable UUID projectId,
            @RequestParam(value = "q", required = false) String q,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<SearchResultResponse> page = searchApplicationService.search(requestContext, projectId, q, pageable);
        return PageResponse.of(page, page.getContent());
    }
}
