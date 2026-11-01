package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.response.VisitHistoryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.service.VisitService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Đợt khám ngoại trú hai pha. Chuyển trạng thái dùng POST theo quy chuẩn Action Endpoint.
 * Hiện có tiếp nhận, hàng đợi, phân công, hủy (NV1); start-exam, services, call-back, postpone-callback, close-visit,
 * abandon (NV2) và reopen (NV5) bổ sung sau.
 */
@RestController
@RequestMapping("/api/v1/clinical/pet-visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    /** UC-19: tiếp nhận khách có hẹn (appointmentId) hoặc vãng lai (petId). */
    @PostMapping("/check-in")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ApiResponse<VisitResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tiếp nhận thành công", visitService.checkIn(request)));
    }

    /** UC-20: hàng đợi chung tại sảnh (cấp cứu -> có hẹn đúng giờ -> vãng lai và khách đến trễ). */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<VisitResponse>>> queue() {
        return ResponseEntity.ok(ApiResponse.success(visitService.waitingQueue()));
    }

    /** Danh sách đợt khám có lọc, phân trang, mới nhất trước (bảng điều phối của lễ tân, trang đợt khám của Admin). */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<VisitResponse>>> search(
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long petId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(visitService.search(status, date, doctorId, petId, keyword, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','LAB_TECHNICIAN','IMAGING_TECHNICIAN','ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(visitService.getById(id)));
    }

    /** Audit trail vòng đời đợt khám. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<VisitHistoryResponse>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(visitService.getHistory(id)));
    }

    /** UC-21: phân công Bác sĩ (kiểm tra BUSY và Khóa ưu tiên). */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public ResponseEntity<ApiResponse<VisitResponse>> assign(@PathVariable Long id,
                                                             @Valid @RequestBody AssignDoctorRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Phân công bác sĩ thành công", visitService.assignDoctor(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> cancel(@PathVariable Long id,
                                                             @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đợt khám", visitService.cancel(id, request)));
    }
}
