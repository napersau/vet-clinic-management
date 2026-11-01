package com.vetclinic.clinical.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * @param imagingDir          thư mục lưu ảnh chẩn đoán (Docker volume /app/uploads/imaging)
 * @param allowedContentTypes các loại tệp được phép tải lên
 */
@ConfigurationProperties(prefix = "vetclinic.storage")
public record StorageProperties(
        @DefaultValue("./uploads/imaging") String imagingDir,
        @DefaultValue({"image/jpeg", "image/png", "application/dicom"}) List<String> allowedContentTypes) {
}
