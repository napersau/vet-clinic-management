package com.vetclinic.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * appointment-service -> notification-service: gửi email/thông báo mã đặt lịch cho chủ nuôi.
 */
public record AppointmentCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long appointmentId,
        String appointmentCode,
        Long ownerId,
        Long petId,
        LocalDate appointmentDate,
        LocalTime startTime,
        String status
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.APPOINTMENT_CREATED;
    }
}
