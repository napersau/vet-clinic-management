package com.vetclinic.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.function.Function;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last; // Đặt tên là 'last' để khớp với JSON property chuẩn của Jackson

    /**
     * Chuyển một Spring Data Page sang PageResponse mà không buộc common-library phụ thuộc spring-data.
     */
    public static <E, T> PageResponse<T> of(List<E> content, int page, int size, long totalElements,
                                            int totalPages, boolean last, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .items(content.stream().map(mapper).toList())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .last(last)
                .build();
    }
}
