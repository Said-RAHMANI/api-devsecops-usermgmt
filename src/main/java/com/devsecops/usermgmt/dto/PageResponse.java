package com.devsecops.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic paginated response envelope.
 *
 * <pre>{
 *   "content":       [...],
 *   "currentPage":   0,
 *   "pageSize":      20,
 *   "totalPages":    5,
 *   "totalElements": 100
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int     currentPage;
    private int     pageSize;
    private int     totalPages;
    private long    totalElements;

    /**
     * Convenience factory: maps a Spring {@link Page} to this DTO.
     */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).collect(Collectors.toList()))
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }
}
