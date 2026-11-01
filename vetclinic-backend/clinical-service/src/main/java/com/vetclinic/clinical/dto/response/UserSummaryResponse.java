package com.vetclinic.clinical.dto.response;

import com.vetclinic.common.security.RoleCode;

public record UserSummaryResponse(
        Long id,
        String fullName,
        String phoneNumber,
        RoleCode role,
        String roomNumber,
        String status
) {
}
