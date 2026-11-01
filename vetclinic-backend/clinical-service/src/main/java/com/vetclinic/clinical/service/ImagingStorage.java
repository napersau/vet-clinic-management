package com.vetclinic.clinical.service;

import com.vetclinic.clinical.config.StorageProperties;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * Lưu và đọc ảnh chẩn đoán hình ảnh trên đĩa (Docker volume).
 * - Tên tệp trên đĩa luôn là UUID do server sinh; tên gốc của người dùng chỉ lưu vào CSDL để hiển thị: không có path traversal.
 * - Kiểm tra cả Content-Type khai báo lẫn chữ ký tệp (magic bytes): đổi đuôi .exe thành .jpg không qua được.
 */
@Slf4j
@Component
public class ImagingStorage {

    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    public record StoredFile(String relativePath, String originalName, long size, String contentType) {
    }

    private final Path root;
    private final StorageProperties properties;

    public ImagingStorage(StorageProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.imagingDir()).toAbsolutePath().normalize();
    }

    /** Kiểm tra mà chưa ghi gì xuống đĩa: gọi cho TẤT CẢ tệp trước khi lưu tệp nào. */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE, "Tệp ảnh rỗng");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE);
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!properties.allowedContentTypes().contains(declared)) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE);
        }
        String detected = detectByMagicBytes(file);
        if (detected == null || !detected.equals(declared)) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE, "Nội dung tệp không khớp định dạng khai báo");
        }
        return detected;
    }

    public StoredFile store(Long visitId, MultipartFile file) {
        String contentType = validate(file);
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".dcm";
        };
        String relative = visitId + "/" + UUID.randomUUID() + extension;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            log.error("Không thể lưu ảnh chẩn đoán {}: {}", relative, e.getMessage());
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "Không thể lưu tệp ảnh, vui lòng thử lại");
        }
        return new StoredFile(relative, sanitizeName(file.getOriginalFilename()), file.getSize(), contentType);
    }

    /** Xóa tệp đã lưu khi bước ghi CSDL phía sau thất bại, để không để lại tệp mồ côi. */
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | AppException e) {
            log.warn("Không xóa được tệp {}: {}", relativePath, e.getMessage());
        }
    }

    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isReadable(path)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Đường dẫn tệp không hợp lệ");
        }
        return path;
    }

    private static String detectByMagicBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(132);
            if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
                return "image/png";
            }
            // DICOM: 128 byte mở đầu rồi chữ "DICM"
            if (head.length >= 132 && head[128] == 'D' && head[129] == 'I' && head[130] == 'C' && head[131] == 'M') {
                return "application/dicom";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String sanitizeName(String original) {
        String name = original == null ? "image" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "image";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
