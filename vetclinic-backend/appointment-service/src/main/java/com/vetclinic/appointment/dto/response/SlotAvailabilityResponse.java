package com.vetclinic.appointment.dto.response;

import java.time.LocalTime;

/**
 * Tình trạng một khung giờ. Giao diện: available -> "Còn {remaining} chỗ"; !available && !past -> "Đã kín lịch".
 *
 * @param capacity  sức chứa tối đa = số Bác sĩ đang ACTIVE
 * @param booked    số lịch hẹn đang chiếm chỗ (PENDING, CONFIRMED, CHECKED_IN)
 * @param remaining số chỗ còn lại
 * @param past      khung giờ đã qua (chỉ xảy ra khi xem ngày hôm nay)
 */
public record SlotAvailabilityResponse(
        LocalTime startTime,
        LocalTime endTime,
        int capacity,
        int booked,
        int remaining,
        boolean available,
        boolean past
) {
}
