package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.DoctorStatus;

public record DoctorStatusResponse(
        Long doctorId,
        String fullName,
        String roomNumber,
        DoctorStatus status
) {
}
