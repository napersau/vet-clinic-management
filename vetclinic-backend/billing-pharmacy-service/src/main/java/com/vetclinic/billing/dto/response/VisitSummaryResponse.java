package com.vetclinic.billing.dto.response;

import com.vetclinic.billing.constant.VisitStatus;

/** Bản rút gọn đợt khám do clinical-service trả về qua endpoint nội bộ. */
public record VisitSummaryResponse(
        Long id,
        String visitCode,
        VisitStatus status,
        Long petId,
        String petName,
        Long ownerId,
        Long doctorId
) {
}
