package com.vetclinic.billing.controller;

import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.service.PrescriptionService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint cho service khác gọi qua OpenFeign. Nằm dưới /internal/**: không cần header người dùng và Gateway không
 * định tuyến ra ngoài, nên chỉ truy cập được trong mạng Docker. Việc kiểm tra "ai được xem" do service gọi đảm nhiệm.
 */
@RestController
@RequestMapping("/internal/billing")
@RequiredArgsConstructor
public class InternalBillingController {

    private final PrescriptionService prescriptionService;
    private final com.vetclinic.billing.service.InvoiceService invoiceService;

    /** clinical-service mở lại đợt khám: hủy hóa đơn PENDING của đợt khám (idempotent; đã thu tiền thì từ chối). */
    @org.springframework.web.bind.annotation.PostMapping("/invoices/by-visit/{visitId}/cancel-for-reopen")
    public ResponseEntity<ApiResponse<Void>> cancelInvoiceForReopen(@org.springframework.web.bind.annotation.PathVariable Long visitId) {
        invoiceService.cancelForReopen(visitId);
        return ResponseEntity.ok(ApiResponse.<Void>success("Đã hủy hóa đơn của đợt khám", null));
    }

    /** clinical-service ghép đơn thuốc (chưa hủy) vào bệnh án trọn đời của thú cưng. */
    @GetMapping("/prescriptions")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> prescriptions(@RequestParam("visitIds") List<Long> visitIds) {
        return ResponseEntity.ok(ApiResponse.success(prescriptionService.findByVisits(visitIds)));
    }
}
