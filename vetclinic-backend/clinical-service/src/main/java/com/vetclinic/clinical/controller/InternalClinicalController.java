package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.response.BillableServiceResponse;
import com.vetclinic.clinical.dto.response.PetSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitSummaryResponse;
import com.vetclinic.clinical.service.PetService;
import com.vetclinic.clinical.service.VisitService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint cho các service khác gọi qua OpenFeign. Nằm dưới /internal/**: không cần header người dùng và Gateway
 * không định tuyến ra ngoài, nên chỉ truy cập được trong mạng Docker.
 */
@RestController
@RequestMapping("/internal/clinical")
@RequiredArgsConstructor
public class InternalClinicalController {

    private final PetService petService;
    private final VisitService visitService;

    /** appointment-service kiểm tra thú cưng tồn tại và thuộc đúng chủ nuôi khi đặt lịch. */
    @GetMapping("/pets/{id}")
    public ResponseEntity<ApiResponse<PetSummaryResponse>> pet(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(petService.getSummary(id)));
    }

    /** appointment-service làm giàu danh sách lịch hẹn bằng tên thú cưng, theo lô. */
    @GetMapping("/pets")
    public ResponseEntity<ApiResponse<List<PetSummaryResponse>>> pets(@RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success(petService.getSummaries(ids)));
    }

    /** billing-pharmacy-service lập hóa đơn: chỉ gồm dịch vụ COMPLETED (dịch vụ đã hủy không tính tiền). */
    @GetMapping("/visits/{id}/billable-services")
    public ResponseEntity<ApiResponse<List<BillableServiceResponse>>> billable(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(visitService.billableServices(id)));
    }

    /** billing-pharmacy-service kiểm tra trạng thái, bác sĩ phụ trách và chủ nuôi của đợt khám (kê đơn, xem đơn). */
    @GetMapping("/visits/{id}")
    public ResponseEntity<ApiResponse<VisitSummaryResponse>> visit(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(visitService.summary(id)));
    }

    /** Làm giàu danh sách hóa đơn / đơn thuốc bằng mã ca khám và tên thú cưng theo lô. */
    @GetMapping("/visits")
    public ResponseEntity<ApiResponse<List<VisitSummaryResponse>>> visits(@RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success(visitService.summaries(ids)));
    }
}
