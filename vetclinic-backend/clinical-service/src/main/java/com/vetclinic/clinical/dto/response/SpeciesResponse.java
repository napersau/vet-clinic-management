package com.vetclinic.clinical.dto.response;

public record SpeciesResponse(
        Long id,
        String code,
        String name,
        String description
) {
}
