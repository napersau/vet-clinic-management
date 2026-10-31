package com.vetclinic.appointment.dto.response;

import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.constant.BookingChannel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        String appointmentCode,
        Long petId,
        Long ownerId,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        BookingChannel bookingChannel,
        AppointmentStatus status,
        String cancelReason,
        Long confirmedBy,
        Instant confirmedAt,
        Long cancelledBy,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        String petName,
        String ownerName,
        String ownerPhone
) {

    /** Gắn tên thú cưng / chủ nuôi (lấy từ clinical và auth). Tham số null thì giữ nguyên giá trị hiện có. */
    public AppointmentResponse withNames(String petName, String ownerName, String ownerPhone) {
        return new AppointmentResponse(id, appointmentCode, petId, ownerId, appointmentDate, startTime, endTime, reason,
                bookingChannel, status, cancelReason, confirmedBy, confirmedAt, cancelledBy, cancelledAt, createdAt, updatedAt,
                petName != null ? petName : this.petName, ownerName != null ? ownerName : this.ownerName,
                ownerPhone != null ? ownerPhone : this.ownerPhone);
    }
}
