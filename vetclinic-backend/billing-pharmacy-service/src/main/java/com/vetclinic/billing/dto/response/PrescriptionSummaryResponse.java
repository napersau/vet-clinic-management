package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.PrescriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PrescriptionSummaryResponse(
        Long id,
        Long visitId,
        String visitCode,
        String petName,
        PrescriptionStatus status,
        int itemCount,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant dispensedAt
) {
}
