package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.request.CreateServiceRequest;
import com.vetclinic.clinical.dto.request.LabTestItemRequest;
import com.vetclinic.clinical.dto.request.ReferenceRangeRequest;
import com.vetclinic.clinical.dto.request.ServiceLabItemsRequest;
import com.vetclinic.clinical.dto.request.UpdateLabTestItemRequest;
import com.vetclinic.clinical.dto.request.SpeciesRequest;
import com.vetclinic.clinical.dto.request.UpdateServiceRequest;
import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.dto.response.ServiceResponse;
import com.vetclinic.clinical.dto.response.SpeciesResponse;
import com.vetclinic.clinical.service.CatalogService;
import com.vetclinic.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Danh mục: loài (UC-07), dịch vụ kỹ thuật (UC-03), chỉ số xét nghiệm và khoảng tham chiếu (UC-05, UC-06 - chỉ xem). */
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/api/v1/species")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SpeciesResponse>>> listSpecies() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.listSpecies()));
    }

    @PostMapping("/api/v1/species")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> createSpecies(@Valid @RequestBody SpeciesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm loài thành công", catalogService.createSpecies(request)));
    }

    @PutMapping("/api/v1/species/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> updateSpecies(@PathVariable Long id,
                                                                      @Valid @RequestBody SpeciesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loài thành công", catalogService.updateSpecies(id, request)));
    }

    /** Bác sĩ chọn dịch vụ để chỉ định (chỉ dịch vụ đang dùng); Admin xem cả dịch vụ đã ngừng (includeInactive=true). */
    @GetMapping("/api/v1/clinical/services")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','IMAGING_TECHNICIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> listServices(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.listServices(!includeInactive)));
    }

    @PostMapping("/api/v1/clinical/services")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(@Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm dịch vụ thành công", catalogService.createService(request)));
    }

    @PutMapping("/api/v1/clinical/services/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(@PathVariable Long id,
                                                                      @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật dịch vụ thành công", catalogService.updateService(id, request)));
    }

    /** UC-05 (+UC-06): thêm chỉ số xét nghiệm kèm khoảng tham chiếu theo loài. */
    @PostMapping("/api/v1/clinical/lab-test-items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LabTestItemResponse>> createLabTestItem(@Valid @RequestBody LabTestItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Thêm chỉ số xét nghiệm thành công", catalogService.createLabTestItem(request)));
    }

    @PutMapping("/api/v1/clinical/lab-test-items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LabTestItemResponse>> updateLabTestItem(@PathVariable Long id,
                                                                              @Valid @RequestBody UpdateLabTestItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật chỉ số thành công", catalogService.updateLabTestItem(id, request)));
    }

    /** UC-06: đặt (thêm hoặc sửa) khoảng tham chiếu của chỉ số cho một loài. Kết quả cũ giữ snapshot khoảng cũ. */
    @PutMapping("/api/v1/clinical/lab-test-items/{id}/ranges/{speciesId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LabTestItemResponse>> saveReferenceRange(@PathVariable Long id, @PathVariable Long speciesId,
                                                                               @Valid @RequestBody ReferenceRangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu khoảng tham chiếu", catalogService.saveReferenceRange(id, speciesId, request)));
    }

    /** Các chỉ số của một dịch vụ xét nghiệm (chưa cấu hình thì trả mọi chỉ số). */
    @GetMapping("/api/v1/clinical/services/{id}/lab-test-items")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<LabTestItemResponse>>> serviceLabTestItems(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.listServiceLabTestItems(id)));
    }

    @PutMapping("/api/v1/clinical/services/{id}/lab-test-items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<LabTestItemResponse>>> setServiceLabTestItems(
            @PathVariable Long id, @Valid @RequestBody ServiceLabItemsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật chỉ số của dịch vụ",
                catalogService.setServiceLabTestItems(id, request.labTestItemIds())));
    }

    @GetMapping("/api/v1/clinical/lab-test-items")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','IMAGING_TECHNICIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<LabTestItemResponse>>> listLabTestItems() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.listLabTestItems()));
    }
}
