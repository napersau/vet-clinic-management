package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.VisitStatus;

/**
 * Cho billing-pharmacy-service: kiểm tra trạng thái / bác sĩ / chủ nuôi khi kê đơn, và làm giàu danh sách hóa đơn
 * bằng mã ca khám, tên thú cưng.
 */
public record VisitSummaryResponse(
        Long id,
        String visitCode,
        VisitStatus status,
        Long petId,
        String petName,
        Long ownerId,
        Long doctorId
) {
}
