package com.vetclinic.appointment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(
        @NotBlank(message = "Lý do hủy / từ chối không được để trống")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
