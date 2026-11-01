package com.vetclinic.clinical.dto.request;

import jakarta.validation.constraints.Size;

/** diagnosis bắt buộc (EX-12), kiểm tra ở service để trả mã CLIN_1312 riêng. */
public record CloseVisitRequest(
        String diagnosis,
        @Size(max = 2000, message = "Phác đồ điều trị tối đa 2000 ký tự") String treatmentPlan,
        @Size(max = 2000, message = "Lời dặn tối đa 2000 ký tự") String doctorAdvice,
        @Size(max = 2000, message = "Ghi chú lâm sàng tối đa 2000 ký tự") String clinicalNotes
) {
}
