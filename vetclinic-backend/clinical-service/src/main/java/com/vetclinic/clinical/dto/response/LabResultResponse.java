package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.LabEvaluation;
import java.math.BigDecimal;
import java.time.Instant;

public record LabResultResponse(
        Long id,
        Long serviceDetailId,
        Long labTestItemId,
        String code,
        String name,
        String unit,
        BigDecimal value,
        BigDecimal minValue,
        BigDecimal maxValue,
        LabEvaluation evaluation,
        Long performedBy,
        String notes,
        Instant createdAt
) {
}
