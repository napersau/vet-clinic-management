package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.request.CreatePetRequest;
import com.vetclinic.clinical.dto.request.UpdatePetRequest;
import com.vetclinic.clinical.dto.response.MedicalHistoryEntry;
import com.vetclinic.clinical.dto.response.PetResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.service.MedicalHistoryService;
import com.vetclinic.clinical.service.PetService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Hồ sơ thú cưng (UC-12, UC-13, UC-18) và dòng thời gian bệnh án (UC-38).
 */
@RestController
@RequestMapping("/api/v1/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;
    private final MedicalHistoryService medicalHistoryService;

    /** Chủ nuôi tự thêm (ownerId bị bỏ qua) hoặc Lễ tân thêm hộ (bắt buộc ownerId). */
    @PostMapping
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<PetResponse>> create(@Valid @RequestBody CreatePetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm hồ sơ thú cưng thành công", petService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PetResponse>>> search(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(petService.search(ownerId, keyword, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','IMAGING_TECHNICIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PetResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(petService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST')")
    public ResponseEntity<ApiResponse<PetResponse>> update(@PathVariable Long id,
                                                           @Valid @RequestBody UpdatePetRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", petService.update(id, request)));
    }

    /**
     * UC-38 / UC-39: bệnh án điện tử trọn đời dạng timeline (chẩn đoán, dịch vụ, kết quả xét nghiệm, ảnh CĐHA, đơn thuốc và
     * hướng dẫn dùng thuốc), phân trang, mới nhất trước. Chủ nuôi chỉ xem được các đợt đã hoàn tất của thú cưng mình.
     */
    @GetMapping("/{id}/medical-history")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<MedicalHistoryEntry>>> medicalHistory(
            @PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(medicalHistoryService.history(id, page, size)));
    }

    /** Danh sách gọn các đợt khám của thú cưng, mới nhất trước. */
    @GetMapping("/{id}/visits")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<VisitResponse>>> visits(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(petService.getVisits(id)));
    }
}
