package com.vetclinic.clinical.dto.response;

/**
 * Một mốc trong bệnh án trọn đời: đợt khám đầy đủ (chẩn đoán, dịch vụ, kết quả xét nghiệm có đánh giá, ảnh CĐHA) kèm đơn
 * thuốc. doctorName và prescription là null nếu chưa có hoặc service liên quan tạm không phản hồi.
 */
public record MedicalHistoryEntry(
        VisitResponse visit,
        String doctorName,
        PrescriptionSummary prescription
) {
}
