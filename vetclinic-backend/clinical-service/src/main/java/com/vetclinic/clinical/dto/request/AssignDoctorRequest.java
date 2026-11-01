package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignDoctorRequest(
        @NotNull(message = "Bác sĩ không được để trống") Long doctorId
) {
}
