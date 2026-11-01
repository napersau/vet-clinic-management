package com.vetclinic.billing.service;

import com.vetclinic.billing.dto.request.MedicineRequest;
import com.vetclinic.billing.dto.request.StockAdjustRequest;
import com.vetclinic.billing.dto.request.StockImportRequest;
import com.vetclinic.billing.dto.request.UpdateMedicineRequest;
import com.vetclinic.billing.dto.response.InventoryTransactionResponse;
import com.vetclinic.billing.dto.response.MedicineResponse;
import com.vetclinic.common.dto.PageResponse;

/** Danh mục thuốc và tồn kho (UC-37). */
public interface MedicineService {

    /** Admin thấy cả thuốc đã ngừng kinh doanh; các vai trò khác chỉ thấy thuốc đang kinh doanh. */
    PageResponse<MedicineResponse> search(String keyword, Boolean active, boolean lowStockOnly, int page, int size);

    MedicineResponse getById(Long id);

    MedicineResponse create(MedicineRequest request);

    MedicineResponse update(Long id, UpdateMedicineRequest request);

    /** FR-66: nhập kho, tăng tồn kho và ghi IMPORT vào nhật ký. */
    MedicineResponse importStock(Long id, StockImportRequest request);

    /** FR-66: cân chỉnh sau kiểm kê (tăng hoặc giảm), ghi ADJUSTMENT vào nhật ký. Không cho tồn kho âm. */
    MedicineResponse adjustStock(Long id, StockAdjustRequest request);

    PageResponse<InventoryTransactionResponse> transactions(Long id, int page, int size);
}
