package com.vetclinic.appointment.dto.internal;

import java.time.LocalTime;

/**
 * Kết quả truy vấn gom nhóm: số lịch hẹn đang chiếm chỗ của từng khung giờ trong một ngày.
 */
public record SlotBookedCount(LocalTime startTime, Long count) {
}
