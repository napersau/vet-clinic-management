package com.vetclinic.common.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * appointment-service -> notification-service: báo cho chủ nuôi khi PHÒNG KHÁM (lễ tân hoặc hệ thống) đổi lịch hẹn của họ:
 * duyệt (CONFIRMED), từ chối / hủy (CANCELLED, kèm lý do), vắng mặt (NO_SHOW) hoặc dời giờ ({@code rescheduled}, trạng thái giữ nguyên).
 * Hành động do chính chủ nuôi thực hiện không phát sự kiện này (họ đã thấy kết quả ngay trên màn hình).
 * UC-17, FR-16.
 */
public record AppointmentStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        Long actorUserId,        // null = hệ thống (tự hủy lịch PENDING quá hạn, tự quét NO_SHOW)
        Long appointmentId,
        String appointmentCode,
        Long ownerId,
        Long petId,
        LocalDate appointmentDate,
        LocalTime startTime,
        String status,           // trạng thái SAU thay đổi
        boolean rescheduled,     // true: chỉ dời giờ, appointmentDate/startTime là giờ MỚI
        String reason            // lý do hủy / từ chối, hoặc ghi chú dời giờ; có thể null
) implements DomainEvent {
    @Override
    public String routingKey() {
        return EventRouting.APPOINTMENT_STATUS_CHANGED;
    }
}
