package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.Size;

/** Khách có hẹn: gửi appointmentId (petId lấy từ lịch hẹn). Khách vãng lai: gửi petId. */
public record CheckInRequest(
        Long petId,
        Long appointmentId,
        @Size(max = 2000, message = "Triệu chứng tối đa 2000 ký tự") String initialSymptoms,
        Boolean emergency
) {
}
