package com.vetclinic.billing.controller;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.billing.service.PrescriptionService;
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

/**
 * Đơn thuốc điện tử. Chuyển trạng thái dùng POST theo quy chuẩn Action Endpoint.
 * Xuất thuốc (POST /{id}/dispense) và hàng đợi phát thuốc (?awaitingDispense=true) thêm ở NV4b cùng hóa đơn.
 */
@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /** UC-31: kê đơn. Gọi TRƯỚC close-visit của clinical-service (ca phải đang IN_PROGRESS). */
    @PostMapping
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> create(@Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đã lưu đơn thuốc", prescriptionService.create(request)));
    }

    /** Sửa đơn (thay toàn bộ dòng thuốc) khi ca còn đang khám. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> update(@PathVariable Long id,
                                                                    @Valid @RequestBody PrescriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật đơn thuốc", prescriptionService.update(id, request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<PrescriptionSummaryResponse>>> search(
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(required = false) Long visitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(prescriptionService.search(status, visitId, page, size)));
    }

    /** UC-39: chủ nuôi chỉ xem được đơn của thú cưng mình. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(prescriptionService.getById(id)));
    }

    @GetMapping("/by-visit/{visitId}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getByVisit(@PathVariable Long visitId) {
        return ResponseEntity.ok(ApiResponse.success(prescriptionService.getByVisit(visitId)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn thuốc", prescriptionService.cancel(id)));
    }
}
