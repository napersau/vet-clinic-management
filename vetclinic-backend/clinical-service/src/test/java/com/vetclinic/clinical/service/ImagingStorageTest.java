package com.vetclinic.clinical.service;

import com.vetclinic.clinical.config.StorageProperties;
import com.vetclinic.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static org.assertj.core.api.Assertions.assertThat;

class ImagingStorageTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @TempDir
    Path root;

    private ImagingStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ImagingStorage(new StorageProperties(root.toString(),
                List.of("image/jpeg", "image/png", "application/dicom")));
    }

    @Test
    void store_writesJpegUnderTheVisitFolderWithAServerGeneratedName() throws IOException {
        ImagingStorage.StoredFile stored = storage.store(12L, new MockMultipartFile("files", "sieu-am.jpg", "image/jpeg", JPEG));

        assertThat(stored.relativePath()).matches("12/[0-9a-f-]{36}\\.jpg");
        assertThat(stored.originalName()).isEqualTo("sieu-am.jpg");
        assertThat(stored.size()).isEqualTo(JPEG.length);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(Files.readAllBytes(root.resolve(stored.relativePath()))).isEqualTo(JPEG);
    }

    @Test
    void store_acceptsPngAndDicom() {
        byte[] dicom = new byte[140];
        dicom[128] = 'D';
        dicom[129] = 'I';
        dicom[130] = 'C';
        dicom[131] = 'M';

        assertThat(storage.store(1L, new MockMultipartFile("files", "x.png", "image/png", PNG)).relativePath()).endsWith(".png");
        assertThat(storage.store(1L, new MockMultipartFile("files", "x.dcm", "application/dicom", dicom)).relativePath()).endsWith(".dcm");
    }

    @Test
    void validate_rejectsAFileWhoseContentDoesNotMatchItsDeclaredType() {
        // Đổi đuôi / khai báo sai: nội dung là văn bản chứ không phải JPEG
        byte[] disguised = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> storage.validate(new MockMultipartFile("files", "a.jpg", "image/jpeg", disguised)));
        // Khai báo PNG nhưng nội dung là JPEG
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> storage.validate(new MockMultipartFile("files", "a.png", "image/png", JPEG)));
    }

    @Test
    void validate_rejectsDisallowedTypesAndEmptyFiles() {
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> storage.validate(new MockMultipartFile("files", "a.pdf", "application/pdf", JPEG)));
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> storage.validate(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[0])));
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> storage.validate(null));
    }

    @Test
    void store_neverWritesOutsideTheStorageRootEvenWithAMaliciousOriginalName() throws IOException {
        ImagingStorage.StoredFile stored = storage.store(7L,
                new MockMultipartFile("files", "../../../etc/evil.jpg", "image/jpeg", JPEG));

        // Tên gốc chỉ để hiển thị (đã bỏ phần đường dẫn); nơi lưu thật do server sinh
        assertThat(stored.originalName()).isEqualTo("evil.jpg");
        assertThat(Files.exists(root.resolve(stored.relativePath()))).isTrue();
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).allSatisfy(f -> assertThat(f.normalize()).startsWith(root));
        }
    }

    @Test
    void load_refusesPathsOutsideTheRoot_andMissingFiles() {
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> storage.load("../secret.txt"));
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> storage.load("1/khong-ton-tai.jpg"));
    }

    @Test
    void loadAndDelete_roundTrip() throws IOException {
        ImagingStorage.StoredFile stored = storage.store(3L, new MockMultipartFile("files", "a.jpg", "image/jpeg", JPEG));

        Resource resource = storage.load(stored.relativePath());
        assertThat(resource.contentLength()).isEqualTo(JPEG.length);

        storage.deleteQuietly(stored.relativePath());
        assertThat(Files.exists(root.resolve(stored.relativePath()))).isFalse();
        storage.deleteQuietly(stored.relativePath());   // xóa lần hai không gây lỗi
        storage.deleteQuietly("../ngoai-root.txt");     // đường dẫn xấu cũng không ném ngoại lệ
    }
}
