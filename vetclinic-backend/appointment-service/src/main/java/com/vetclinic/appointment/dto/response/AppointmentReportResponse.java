package com.vetclinic.appointment.dto.response;

import java.time.LocalDate;
import java.util.Map;

/**
 * Báo cáo lịch hẹn theo ngày hẹn (FR-70). noShowRate = NO_SHOW / (CHECKED_IN + NO_SHOW): trong các lịch đã có kết cục,
 * bao nhiêu phần trăm khách bỏ hẹn. cancellationRate = CANCELLED / tổng. Cả hai là tỉ lệ 0..1, làm tròn 4 chữ số.
 */
public record AppointmentReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalAppointments,
        Map<String, Long> byStatus,
        Map<String, Long> byChannel,
        double noShowRate,
        double cancellationRate
) {
}
