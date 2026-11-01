package com.vetclinic.clinical.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Quy tắc "có hẹn đúng giờ" của hàng đợi sảnh (nghiệp vụ §8.4): chỉ phụ thuộc giờ hẹn và giờ check-in, không phụ thuộc DB. */
public final class QueueSchedule {

    /** Độ dài một khung giờ khi lịch hẹn không trả giờ kết thúc (BookingProperties của appointment-service: 30 phút). */
    static final Duration DEFAULT_SLOT = Duration.ofMinutes(30);

    private QueueSchedule() {
    }

    /**
     * Trả giờ bắt đầu khung hẹn nếu lúc này (theo giờ phòng khám của clock) CHƯA quá hết khung giờ; đến sớm vẫn tính đúng giờ.
     * Trả null nếu đã trễ quá hết khung (xếp như khách vãng lai) hoặc lịch hẹn thiếu giờ.
     */
    public static Instant scheduledAtIfOnTime(LocalDate date, LocalTime start, LocalTime end, Clock clock) {
        if (date == null || start == null) {
            return null;
        }
        Instant slotStart = date.atTime(start).atZone(clock.getZone()).toInstant();
        Instant slotEnd = end != null ? date.atTime(end).atZone(clock.getZone()).toInstant() : slotStart.plus(DEFAULT_SLOT);
        return clock.instant().isAfter(slotEnd) ? null : slotStart;
    }
}
