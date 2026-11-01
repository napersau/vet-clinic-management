package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ServiceDetailResponse(
        Long id,
        Long visitId,
        Long serviceId,
        String serviceCode,
        String serviceName,
        ServiceCategory category,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        ServiceDetailStatus status,
        String notes,
        Instant createdAt
) {
}
