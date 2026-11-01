package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.VisitStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Hồ sơ một đợt khám. Danh sách dịch vụ (services) thêm ở NV2, kết quả xét nghiệm / ảnh CĐHA (labResults, imagingResults)
 * thêm ở NV3, chỉ có ở API chi tiết.
 * locked = true từ lúc Bác sĩ chốt chuyên môn (WAITING_PAYMENT trở đi): không được sửa chẩn đoán, dịch vụ, đơn thuốc (BR-27).
 * scheduledAt != null: khách có hẹn check-in đúng khung giờ (ưu tiên 2 của hàng đợi sảnh).
 */
public record VisitResponse(
        Long id,
        String visitCode,
        Long petId,
        String petName,
        String petCode,
        String speciesName,
        Long ownerId,
        Long appointmentId,
        Long doctorId,
        Long receptionistId,
        String assignedRoom,
        boolean emergency,
        VisitStatus status,
        boolean locked,
        boolean callbackPostponed,
        BigDecimal weightAtVisit,
        BigDecimal temperature,
        String initialSymptoms,
        String clinicalNotes,
        String diagnosis,
        String treatmentPlan,
        String doctorAdvice,
        String cancelReason,
        Instant checkedInAt,
        Instant scheduledAt,
        Instant startedAt,
        Instant closedAt,
        Instant completedAt
) {
}
