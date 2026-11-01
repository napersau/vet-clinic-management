package com.vetclinic.billing.controller;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.dto.request.DiscountRequest;
import com.vetclinic.billing.dto.response.InvoiceResponse;
import com.vetclinic.billing.dto.response.InvoiceSummaryResponse;
import com.vetclinic.billing.service.InvoiceService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Hóa đơn viện phí (thu ngân = Lễ tân). Hóa đơn PENDING được hệ thống tự lập khi Bác sĩ chốt ca. */
@RestController
@RequestMapping("/api/v1/billing/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /** Danh sách chờ thanh toán: status=PENDING&ascending=true (ca vào trước phục vụ trước). */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceSummaryResponse>>> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long visitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean ascending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                invoiceService.search(status, code, visitId, fromDate, toDate, ascending, page, size)));
    }

    /** Bảng kê chi tiết (cũng là phiếu tạm tính khi còn PENDING). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getById(id)));
    }

    @GetMapping("/by-visit/{visitId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByVisit(@PathVariable Long visitId) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getByVisit(visitId)));
    }

    /** UC-34: nhập chiết khấu (không vượt tổng viện phí). */
    @PutMapping("/{id}/discount")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> applyDiscount(@PathVariable Long id,
                                                                      @Valid @RequestBody DiscountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật chiết khấu", invoiceService.applyDiscount(id, request)));
    }

    /** UC-35: xác nhận đã thu tiền. */
    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> confirmPayment(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận thanh toán", invoiceService.confirmPayment(id)));
    }

    /** SM-05: hủy hóa đơn PENDING do sai sót (cần lý do). Đã thu tiền thì không hủy được. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> cancel(@PathVariable Long id,
                                                               @Valid @RequestBody com.vetclinic.billing.dto.request.CancelInvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy hóa đơn", invoiceService.cancel(id, request.reason())));
    }

    /** Cứu hộ khi sự kiện chốt ca bị mất: lập hóa đơn cho đợt khám đang chờ thanh toán (idempotent). */
    @PostMapping("/from-visit/{visitId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generateForVisit(@PathVariable Long visitId) {
        return ResponseEntity.ok(ApiResponse.success("Đã lập hóa đơn", invoiceService.generateForVisit(visitId)));
    }
}
