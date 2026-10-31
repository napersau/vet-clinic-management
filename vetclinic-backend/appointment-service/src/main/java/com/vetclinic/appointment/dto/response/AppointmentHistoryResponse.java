package com.vetclinic.appointment.dto.response;

import com.vetclinic.appointment.constant.AppointmentStatus;

import java.time.Instant;

/**
 * changedByUserId = null nghĩa là hệ thống tự động.
 */
public record AppointmentHistoryResponse(
        Long id,
        AppointmentStatus fromStatus,
        AppointmentStatus toStatus,
        Long changedByUserId,
        String notes,
        Instant createdAt
) {
}
