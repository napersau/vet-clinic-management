package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * billing-pharmacy-service -> clinical-service (chuyển visit WAITING_PAYMENT -> COMPLETED, idempotent), notification-service.
 * Chỉ phát khi ca khám xong hẳn: hóa đơn PAID và đơn thuốc DISPENSED (hoặc ca không kê đơn).
 * actorUserId = thu ngân, dùng làm changed_by_user_id khi ghi pet_visit_status_histories.
 */
public record PaymentAndDispenseCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long visitId,
        Long invoiceId,
        Long prescriptionId   // null nếu không kê đơn
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.PAYMENT_COMPLETED;
    }
}
