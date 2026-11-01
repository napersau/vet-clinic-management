package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.VisitStatus;
import java.time.Instant;

public record VisitHistoryResponse(
        Long id,
        VisitStatus fromStatus,
        VisitStatus toStatus,
        Long changedByUserId,
        String notes,
        Instant createdAt
) {
}
