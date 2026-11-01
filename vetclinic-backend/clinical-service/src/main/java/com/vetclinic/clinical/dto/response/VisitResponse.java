package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.VisitStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Hồ sơ một đợt khám. services / labResults / imagingResults chỉ có ở API chi tiết, null ở các API danh sách.
 * locked = true từ lúc Bác sĩ chốt chuyên môn (WAITING_PAYMENT trở đi): không được sửa chẩn đoán, dịch vụ, đơn thuốc (BR-27).
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
        Instant completedAt,
        List<ServiceDetailResponse> services,
        List<LabResultResponse> labResults,
        List<ImagingResultResponse> imagingResults
) {
}
