package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.ServiceCategory;
import java.math.BigDecimal;

public record ServiceResponse(
        Long id,
        String code,
        String name,
        ServiceCategory category,
        BigDecimal price,
        String description,
        boolean active
) {
}
