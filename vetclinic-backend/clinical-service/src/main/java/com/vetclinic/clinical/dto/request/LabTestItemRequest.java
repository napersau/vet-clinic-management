package com.vetclinic.clinical.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Thêm chỉ số xét nghiệm (UC-05); ranges (UC-06) bỏ trống được và bổ sung sau bằng PUT /{id}/ranges/{speciesId}. */
public record LabTestItemRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String unit,
        @Size(max = 255) String description,
        @Size(max = 20) List<@Valid ReferenceRangeInput> ranges
) {
}
