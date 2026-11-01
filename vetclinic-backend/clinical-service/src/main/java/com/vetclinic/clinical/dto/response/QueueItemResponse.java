package com.vetclinic.clinical.dto.response;

import com.vetclinic.clinical.constant.QueueState;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import java.time.Instant;

/**
 * Một dòng trong hàng đợi phòng Lab / CĐHA. otherRoomStatus: tình trạng dịch vụ cùng đợt khám ở phòng còn lại
 * (huy hiệu liên phòng FR-36), vd. LAB_IN_PROGRESS, LAB_PENDING, LAB_DONE, IMAGING_PENDING...
 */
public record QueueItemResponse(
        Long serviceDetailId,
        Long visitId,
        String visitCode,
        Long petId,
        String petName,
        String speciesName,
        Long serviceId,
        String serviceName,
        ServiceCategory category,
        boolean emergency,
        ServiceDetailStatus status,
        QueueState queueState,
        String otherRoomStatus,
        String notes,
        Instant orderedAt
) {
}
