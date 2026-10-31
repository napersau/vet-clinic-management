package com.vetclinic.appointment.service;

import com.vetclinic.appointment.constant.AppointmentStatus;

import java.time.LocalDate;

/**
 * Điều kiện tra cứu lịch hẹn. Với PET_OWNER, ownerId luôn bị ghi đè bằng chính người đăng nhập.
 *
 * @param sortBy "createdAt" (thời gian gửi yêu cầu, dùng cho hàng chờ duyệt UC-17) hoặc mặc định theo giờ hẹn
 */
public record AppointmentFilter(
        Long ownerId,
        Long petId,
        String code,
        LocalDate fromDate,
        LocalDate toDate,
        AppointmentStatus status,
        String sortBy,
        boolean ascending,
        int page,
        int size
) {
}
