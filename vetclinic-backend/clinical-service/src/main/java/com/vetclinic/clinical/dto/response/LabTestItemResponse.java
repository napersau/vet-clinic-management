package com.vetclinic.clinical.dto.response;

import java.util.List;

public record LabTestItemResponse(
        Long id,
        String code,
        String name,
        String unit,
        String description,
        List<ReferenceRangeResponse> ranges
) {
}
