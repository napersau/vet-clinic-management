package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * clinical-service -> notification-service, appointment-service.
 * appointment-service dùng sự kiện này để chuyển lịch hẹn sang CHECKED_IN (idempotent), vì
 * check-in không được ghi chéo sang DB của service khác trong cùng một transaction.
 */
public record VisitCheckedInEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long visitId,
        String visitCode,
        Long petId,
        Long appointmentId,   // null nếu khách vãng lai (walk-in)
        boolean emergency
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.VISIT_CHECKED_IN;
    }
}
