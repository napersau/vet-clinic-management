package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.CloseVisitRequest;
import com.vetclinic.clinical.dto.request.OrderServicesRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.request.StartExamRequest;
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
 * Mở lại ca đã chốt (POST /{id}/reopen, chỉ Admin) thêm ở NV5 vì cần hủy hóa đơn bên billing.
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

    /** UC-22: Bác sĩ bắt đầu khám, tự đo cân nặng và thân nhiệt. */
    @PostMapping("/{id}/start-exam")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> startExam(@PathVariable Long id,
                                                                @Valid @RequestBody StartExamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bác sĩ đã bắt đầu khám lâm sàng", visitService.startExam(id, request)));
    }

    /** UC-23: chỉ định dịch vụ. Có Lab / CĐHA thì ca sang WAITING_RESULTS và Bác sĩ được giải phóng. */
    @PostMapping("/{id}/services")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> orderServices(@PathVariable Long id,
                                                                    @Valid @RequestBody OrderServicesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã chỉ định dịch vụ", visitService.orderServices(id, request)));
    }

    /** UC-30: mời ca đã có kết quả vào phòng (GĐ2), cũng là Recall sau khi tạm hoãn. */
    @PostMapping("/{id}/call-back")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> callBack(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã mời bệnh nhân vào phòng", visitService.callBack(id)));
    }

    /** EX-10: khách vắng khi gọi vào GĐ2. */
    @PostMapping("/{id}/postpone-callback")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> postponeCallback(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạm hoãn ca tái khám", visitService.postponeCallback(id)));
    }

    /** UC-33: chốt chuyên môn, khóa hồ sơ, chuyển sang thanh toán. Bắt buộc có chẩn đoán xác định. */
    @PostMapping("/{id}/close-visit")
    @PreAuthorize("hasRole('VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> closeVisit(@PathVariable Long id,
                                                                 @Valid @RequestBody CloseVisitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã chốt đợt khám, chuyển sang thanh toán", visitService.closeVisit(id, request)));
    }

    /** EX-11: khách bỏ về hẳn không đọc kết quả. */
    @PostMapping("/{id}/abandon")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> abandon(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã đóng ca, ghi nợ viện phí", visitService.abandon(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','VETERINARIAN')")
    public ResponseEntity<ApiResponse<VisitResponse>> cancel(@PathVariable Long id,
                                                             @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đợt khám", visitService.cancel(id, request)));
    }
}
