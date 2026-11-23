package com.studychain.api.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Our own page shape, so the API does not depend on Spring's internal Page serialization. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
