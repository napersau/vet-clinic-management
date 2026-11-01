package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.PetGender;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PetResponse(
        Long id,
        Long ownerId,
        Long speciesId,
        String speciesCode,
        String speciesName,
        String petCode,
        String name,
        String breed,
        PetGender gender,
        LocalDate dateOfBirth,
        String color,
        BigDecimal weight,
        boolean neutered,
        String identifyingCharacteristics,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
