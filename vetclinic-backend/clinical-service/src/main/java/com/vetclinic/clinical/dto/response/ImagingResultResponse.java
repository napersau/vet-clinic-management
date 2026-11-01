package com.vetclinic.clinical.dto.response;

import java.time.Instant;
import java.util.List;

public record ImagingResultResponse(
        Long id,
        Long serviceDetailId,
        String description,
        String conclusion,
        Long performedBy,
        Instant performedAt,
        List<AttachmentResponse> attachments
) {
}
