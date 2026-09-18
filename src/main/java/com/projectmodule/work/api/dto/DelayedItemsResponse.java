package com.projectmodule.work.api.dto;

import java.util.List;

/** A project's delayed items, per {@code docs/project/22-DELAY-DETECTION-SPEC.md}. */
public record DelayedItemsResponse(List<DelayedItemResponse> items) {
}
