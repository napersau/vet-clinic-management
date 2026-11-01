package com.vetclinic.clinical.dto.response;

public record AttachmentResponse(
        Long id,
        String fileName,
        Long fileSize,
        String contentType,
        String downloadUrl
) {
}
