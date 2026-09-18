package com.projectmodule.work.api.dto;

import java.util.List;

/** Response for a bulk-archive endpoint, per {@code docs/project/20-BULK-ACTIONS-SPEC.md} §4. */
public record BulkArchiveResponse(List<BulkArchiveResultResponse> results) {
}
