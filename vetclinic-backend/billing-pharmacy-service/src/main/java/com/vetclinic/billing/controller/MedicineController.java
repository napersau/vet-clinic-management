package com.vetclinic.billing.controller;

import com.vetclinic.billing.dto.request.MedicineRequest;
import com.vetclinic.billing.dto.request.StockAdjustRequest;
import com.vetclinic.billing.dto.request.StockImportRequest;
import com.vetclinic.billing.dto.request.UpdateMedicineRequest;
import com.vetclinic.billing.dto.response.InventoryTransactionResponse;
import com.vetclinic.billing.dto.response.MedicineResponse;
import com.vetclinic.billing.service.MedicineService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Danh mục thuốc và tồn kho. Thuốc đã phát sinh giao dịch không xóa cứng: ngừng kinh doanh bằng active = false. */
@RestController
@RequestMapping("/api/v1/pharmacy/medicines")
@RequiredArgsConstructor
public class MedicineController {

    private final MedicineService medicineService;

    /** Tra cứu khi kê đơn: kèm hoạt chất, đơn vị, giá bán và tồn kho khả dụng. */
    @GetMapping
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<MedicineResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(medicineService.search(keyword, active, lowStock, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VETERINARIAN','RECEPTIONIST','ADMIN')")
    public ResponseEntity<ApiResponse<MedicineResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(medicineService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MedicineResponse>> create(@Valid @RequestBody MedicineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Đã thêm thuốc vào danh mục", medicineService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MedicineResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateMedicineRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật thuốc", medicineService.update(id, request)));
    }

    /** UC-37 / FR-66: nhập kho. */
    @PostMapping("/{id}/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MedicineResponse>> importStock(@PathVariable Long id,
                                                                     @Valid @RequestBody StockImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã nhập kho", medicineService.importStock(id, request)));
    }

    /** UC-37 / FR-66: cân chỉnh kho sau kiểm kê (tăng hoặc giảm). */
    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MedicineResponse>> adjustStock(@PathVariable Long id,
                                                                     @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cân chỉnh kho", medicineService.adjustStock(id, request)));
    }

    /** Nhật ký biến động kho (audit), mới nhất trước. */
    @GetMapping("/{id}/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> transactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(medicineService.transactions(id, page, size)));
    }
}
