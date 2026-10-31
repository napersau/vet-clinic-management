package com.vetclinic.appointment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleAppointmentRequest(
        @NotNull(message = "Ngày hẹn mới không được để trống")
        LocalDate newDate,

        @NotNull(message = "Khung giờ mới không được để trống")
        LocalTime newStartTime,

        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason
) {
}
