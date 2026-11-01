package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.InventoryTransactionType;
import com.vetclinic.billing.dto.request.MedicineRequest;
import com.vetclinic.billing.dto.request.StockAdjustRequest;
import com.vetclinic.billing.dto.request.StockImportRequest;
import com.vetclinic.billing.dto.request.UpdateMedicineRequest;
import com.vetclinic.billing.dto.response.InventoryTransactionResponse;
import com.vetclinic.billing.dto.response.MedicineResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.mapper.MedicineMapper;
import com.vetclinic.billing.repository.InventoryTransactionRepository;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.repository.MedicineSpecifications;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.MedicineService;
import com.vetclinic.billing.service.StockAlertPublisher;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MedicineServiceImpl implements MedicineService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MedicineRepository medicineRepository;
    private final InventoryTransactionRepository inventoryRepository;
    private final MedicineMapper mapper;
    private final BillingAssembler assembler;
    private final StockAlertPublisher stockAlerts;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MedicineResponse> search(String keyword, Boolean active, boolean lowStockOnly, int page, int size) {
        // Chỉ Admin được xem thuốc đã ngừng kinh doanh; còn lại luôn bị ép lọc đang kinh doanh
        Boolean effectiveActive = isAdmin() ? active : Boolean.TRUE;
        Page<Medicine> result = medicineRepository.findAll(
                MedicineSpecifications.filter(keyword, effectiveActive, lowStockOnly),
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("name").ascending().and(Sort.by("id"))));
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicineResponse getById(Long id) {
        Medicine medicine = find(id);
        if (!medicine.isActive() && !isAdmin()) {
            throw new AppException(ErrorCode.MEDICINE_NOT_FOUND);
        }
        return mapper.toResponse(medicine);
    }

    @Override
    @Transactional
    public MedicineResponse create(MedicineRequest request) {
        Long actor = SecurityUtils.getCurrentUserId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (medicineRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS);
        }
        int initialStock = request.initialStock() == null ? 0 : request.initialStock();
        Medicine medicine = Medicine.builder()
                .code(code)
                .name(request.name().trim())
                .activeIngredient(blankToNull(request.activeIngredient()))
                .dosageForm(request.dosageForm().trim())
                .unit(request.unit().trim())
                .salePrice(InvoiceCalculator.money(request.salePrice()))
                .stockQuantity(initialStock)
                .minStockAlert(request.minStockAlert() == null ? 10 : request.minStockAlert())
                .build();
        try {
            medicine = medicineRepository.saveAndFlush(medicine);
        } catch (DataIntegrityViolationException e) {
            // Hai Admin cùng tạo một mã: ràng buộc UNIQUE là chốt chặn cuối
            throw new AppException(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS);
        }
        if (initialStock > 0) {
            log(medicine.getId(), InventoryTransactionType.IMPORT, initialStock, initialStock, null,
                    "Tồn kho ban đầu khi tạo thuốc", actor);
        }
        return mapper.toResponse(medicine);
    }

    @Override
    @Transactional
    public MedicineResponse update(Long id, UpdateMedicineRequest request) {
        Medicine medicine = find(id);
        medicine.setName(request.name().trim());
        medicine.setActiveIngredient(blankToNull(request.activeIngredient()));
        medicine.setDosageForm(request.dosageForm().trim());
        medicine.setUnit(request.unit().trim());
        medicine.setSalePrice(InvoiceCalculator.money(request.salePrice()));
        if (request.minStockAlert() != null) {
            medicine.setMinStockAlert(request.minStockAlert());
        }
        if (request.active() != null) {
            medicine.setActive(request.active());
        }
        // @DynamicUpdate: chỉ ghi các cột vừa đổi, không đụng tới stock_quantity
        return mapper.toResponse(medicineRepository.saveAndFlush(medicine));
    }

    @Override
    @Transactional
    public MedicineResponse importStock(Long id, StockImportRequest request) {
        Long actor = SecurityUtils.getCurrentUserId();
        Medicine medicine = find(id);
        applyDelta(medicine, request.quantity());
        log(id, InventoryTransactionType.IMPORT, request.quantity(), medicine.getStockQuantity(), null,
                blankToNull(request.notes()), actor);
        return mapper.toResponse(medicine);
    }

    @Override
    @Transactional
    public MedicineResponse adjustStock(Long id, StockAdjustRequest request) {
        Long actor = SecurityUtils.getCurrentUserId();
        if (request.delta() == 0) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Số lượng điều chỉnh phải khác 0");
        }
        Medicine medicine = find(id);
        applyDelta(medicine, request.delta());
        log(id, InventoryTransactionType.ADJUSTMENT, request.delta(), medicine.getStockQuantity(), null,
                request.reason().trim(), actor);
        if (request.delta() < 0) {
            stockAlerts.publishIfLow(medicine, actor);
        }
        return mapper.toResponse(medicine);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> transactions(Long id, int page, int size) {
        find(id);
        Page<InventoryTransaction> result = inventoryRepository.findByMedicineIdOrderByIdDesc(id,
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), assembler::toResponse);
    }

    // ================================================================== helper

    private Medicine find(Long id) {
        return medicineRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.MEDICINE_NOT_FOUND));
    }

    /**
     * Đổi tồn kho bằng UPDATE nguyên tử rồi refresh để lấy số thật: không gán stockQuantity trên entity (xem Medicine).
     * 0 dòng = tồn kho sẽ âm (thuốc đã được find nên chắc chắn tồn tại).
     */
    private void applyDelta(Medicine medicine, int delta) {
        if (medicineRepository.addStock(medicine.getId(), delta) == 0) {
            throw new AppException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Điều chỉnh làm tồn kho của thuốc [" + medicine.getName() + "] bị âm");
        }
        entityManager.refresh(medicine);
    }

    private void log(Long medicineId, InventoryTransactionType type, int quantity, int balanceAfter, Long referenceId,
                     String notes, Long actor) {
        inventoryRepository.save(InventoryTransaction.builder().medicineId(medicineId).transactionType(type)
                .quantity(quantity).balanceAfter(balanceAfter).referenceId(referenceId).notes(notes).createdBy(actor)
                .build());
    }

    private static boolean isAdmin() {
        return SecurityUtils.getCurrentRole().filter(r -> r == RoleCode.ADMIN).isPresent();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
