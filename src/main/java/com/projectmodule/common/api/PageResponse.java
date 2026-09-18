package com.projectmodule.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A page of results, independent of any particular entity type.
 *
 * <p>{@link Page} itself is not returned from controllers: its JSON shape is a Spring Data
 * implementation detail that has changed across versions, and returning it directly would
 * couple the API contract to that library. This record is the stable shape callers see.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /** Builds a response from a Spring Data page whose content has already been mapped. */
    public static <T> PageResponse<T> of(Page<?> source, List<T> mappedContent) {
        return new PageResponse<>(
                mappedContent,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
