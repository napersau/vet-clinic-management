package com.vetclinic.clinical.dto.response;

import java.math.BigDecimal;

public record ReferenceRangeResponse(
        Long speciesId,
        String speciesCode,
        BigDecimal minValue,
        BigDecimal maxValue
) {
}
