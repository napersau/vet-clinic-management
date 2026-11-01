package com.vetclinic.clinical.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo hiệu suất vận hành (FR-70) theo ngày check-in. routineVisits = ca không phát sinh cận lâm sàng,
 * paraclinicalVisits = ca có ít nhất một chỉ định xét nghiệm hoặc chẩn đoán hình ảnh.
 */
public record OperationsReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalVisits,
        long completedVisits,
        long cancelledVisits,
        long emergencyVisits,
        long routineVisits,
        long paraclinicalVisits,
        Map<String, Long> byStatus,
        List<DoctorLoad> doctors
) {

    /** Số ca một Bác sĩ phụ trách trong kỳ (ca đã được phân công cho bác sĩ đó). */
    public record DoctorLoad(Long doctorId, String doctorName, long assignedVisits, long completedVisits) {
    }
}
