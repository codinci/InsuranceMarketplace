package com.example.insurance.policyservice.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of a list (PLAN 3: pagination on every list). */
@Schema(description = "One page of results.")
public record PageResponse<T>(
    @Schema(description = "The items on this page.") List<T> content,
    @Schema(description = "Page number, starting at 0.", example = "0") int page,
    @Schema(description = "Requested page size.", example = "20") int size,
    @Schema(description = "Items across all pages.", example = "42") long totalElements,
    @Schema(description = "Number of pages.", example = "3") int totalPages) {

  public PageResponse {
    content = List.copyOf(content);
  }

  public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
