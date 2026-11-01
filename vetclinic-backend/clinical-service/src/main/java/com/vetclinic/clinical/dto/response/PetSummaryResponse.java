package com.vetclinic.clinical.dto.response;

/** Cho appointment-service kiểm tra thú cưng tồn tại và đúng chủ nuôi. */
public record PetSummaryResponse(
        Long id,
        Long ownerId,
        String name
) {
}
