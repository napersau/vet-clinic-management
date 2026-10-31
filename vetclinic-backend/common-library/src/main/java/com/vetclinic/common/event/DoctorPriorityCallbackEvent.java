package com.vetclinic.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * clinical-service -> notification-service: ca khám đã có đủ kết quả CLS (RESULTS_READY),
 * bật huy hiệu / popup Khóa ưu tiên trên màn hình Bác sĩ phụ trách.
 */
public record DoctorPriorityCallbackEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,
        Long visitId,
        String visitCode,
        Long doctorId,
        Long petId
) implements DomainEvent {

    @Override
    public String routingKey() {
        return EventRouting.DOCTOR_PRIORITY_CALLBACK;
    }
}
