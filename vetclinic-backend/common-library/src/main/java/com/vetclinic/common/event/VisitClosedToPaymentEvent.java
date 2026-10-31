package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * clinical-service -> billing-pharmacy-service (tạo hóa đơn PENDING, idempotent theo visit_id), notification-service.
 * abandoned = true khi đóng ca do khách bỏ về (EX-11): visit đã COMPLETED nhưng hóa đơn vẫn PENDING (ghi nợ).
 */
public record VisitClosedToPaymentEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long visitId,
        String visitCode,
        Long petId,
        Long doctorId,
        boolean abandoned
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.VISIT_CLOSED_TO_PAYMENT;
    }
}
