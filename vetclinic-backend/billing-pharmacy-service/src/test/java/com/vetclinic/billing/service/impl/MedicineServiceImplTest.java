package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.InventoryTransactionType;
import com.vetclinic.billing.dto.request.MedicineRequest;
import com.vetclinic.billing.dto.request.StockAdjustRequest;
import com.vetclinic.billing.dto.request.StockImportRequest;
import com.vetclinic.billing.dto.request.UpdateMedicineRequest;
import com.vetclinic.billing.dto.response.MedicineResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.mapper.MedicineMapperImpl;
import com.vetclinic.billing.repository.InventoryTransactionRepository;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.StockAlertPublisher;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static com.vetclinic.billing.testsupport.TestData.DOCTOR;
import static com.vetclinic.billing.testsupport.TestData.assertError;
import static com.vetclinic.billing.testsupport.TestData.loginAs;
import static com.vetclinic.billing.testsupport.TestData.medicine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicineServiceImplTest {

    private static final long ADMIN = 1L;

    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private InventoryTransactionRepository inventoryRepository;
    @Mock
    private StockAlertPublisher stockAlerts;
    @Mock
    private EntityManager entityManager;

    private MedicineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicineServiceImpl(medicineRepository, inventoryRepository, new MedicineMapperImpl(),
                new BillingAssembler(), stockAlerts, entityManager);
        lenient().when(medicineRepository.saveAndFlush(any(Medicine.class))).thenAnswer(inv -> {
            Medicine medicine = inv.getArgument(0);
            if (medicine.getId() == null) {
                medicine.setId(77L);
            }
            return medicine;
        });
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    /** Mô phỏng UPDATE nguyên tử rồi refresh(): sau refresh entity mang tồn kho thật. */
    private void stockBecomes(Medicine medicine, int newStock) {
        lenient().doAnswer(inv -> {
            medicine.setStockQuantity(newStock);
            return null;
        }).when(entityManager).refresh(medicine);
    }

    @Test
    void importStock_increasesStockAndLogsImportWithBalance() {
        loginAs(ADMIN, RoleCode.ADMIN);
        Medicine medicine = medicine(1L, "Clavamox", "15000", 20);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
        when(medicineRepository.addStock(1L, 100)).thenReturn(1);
        stockBecomes(medicine, 120);

        MedicineResponse response = service.importStock(1L, new StockImportRequest(100, "Nhập lô tháng 10"));

        assertThat(response.stockQuantity()).isEqualTo(120);
        ArgumentCaptor<InventoryTransaction> log = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryRepository).save(log.capture());
        assertThat(log.getValue().getTransactionType()).isEqualTo(InventoryTransactionType.IMPORT);
        assertThat(log.getValue().getQuantity()).isEqualTo(100);
        assertThat(log.getValue().getBalanceAfter()).isEqualTo(120);
        assertThat(log.getValue().getCreatedBy()).isEqualTo(ADMIN);
    }

    @Test
    void adjustStock_downRecordsSignedQuantityAndRaisesLowStockAlert() {
        loginAs(ADMIN, RoleCode.ADMIN);
        Medicine medicine = medicine(1L, "Clavamox", "15000", 20);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
        when(medicineRepository.addStock(1L, -12)).thenReturn(1);
        stockBecomes(medicine, 8);

        service.adjustStock(1L, new StockAdjustRequest(-12, "Hết hạn sử dụng"));

        ArgumentCaptor<InventoryTransaction> log = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryRepository).save(log.capture());
        assertThat(log.getValue().getTransactionType()).isEqualTo(InventoryTransactionType.ADJUSTMENT);
        assertThat(log.getValue().getQuantity()).isEqualTo(-12);
        assertThat(log.getValue().getBalanceAfter()).isEqualTo(8);
        assertThat(log.getValue().getNotes()).isEqualTo("Hết hạn sử dụng");
        verify(stockAlerts).publishIfLow(medicine, ADMIN);   // 8 <= ngưỡng 10
    }

    @Test
    void adjustStock_upDoesNotRaiseAnAlert() {
        loginAs(ADMIN, RoleCode.ADMIN);
        Medicine medicine = medicine(1L, "Clavamox", "15000", 5);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));
        when(medicineRepository.addStock(1L, 30)).thenReturn(1);
        stockBecomes(medicine, 35);

        service.adjustStock(1L, new StockAdjustRequest(30, "Kiểm kê thừa"));

        verify(stockAlerts, never()).publishIfLow(any(), anyLong());
    }

    @Test
    void adjustStock_thatWouldMakeStockNegativeIsRefusedAndNothingIsLogged() {
        loginAs(ADMIN, RoleCode.ADMIN);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine(1L, "Clavamox", "15000", 5)));
        when(medicineRepository.addStock(1L, -50)).thenReturn(0);   // điều kiện stock + delta >= 0 không thỏa

        assertError(ErrorCode.INVALID_STOCK_ADJUSTMENT, () -> service.adjustStock(1L, new StockAdjustRequest(-50, "Mất")));
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void adjustStock_byZeroIsRefused() {
        loginAs(ADMIN, RoleCode.ADMIN);

        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.adjustStock(1L, new StockAdjustRequest(0, "Không đổi")));
        verify(medicineRepository, never()).addStock(anyLong(), anyInt());
    }

    @Test
    void create_normalizesCodeAndLogsOpeningStock() {
        loginAs(ADMIN, RoleCode.ADMIN);
        when(medicineRepository.existsByCodeIgnoreCase("MED_NEW")).thenReturn(false);

        MedicineResponse response = service.create(new MedicineRequest(" med_new ", "Thuốc mới", null, "Viên nén", "Viên",
                new BigDecimal("2500"), 40, null));

        assertThat(response.code()).isEqualTo("MED_NEW");
        assertThat(response.stockQuantity()).isEqualTo(40);
        assertThat(response.minStockAlert()).isEqualTo(10);   // mặc định
        ArgumentCaptor<InventoryTransaction> log = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryRepository).save(log.capture());
        assertThat(log.getValue().getTransactionType()).isEqualTo(InventoryTransactionType.IMPORT);
        assertThat(log.getValue().getBalanceAfter()).isEqualTo(40);
    }

    @Test
    void create_duplicateCodeIsRefused() {
        loginAs(ADMIN, RoleCode.ADMIN);
        when(medicineRepository.existsByCodeIgnoreCase("MED_AMOX")).thenReturn(true);

        assertError(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS, () -> service.create(new MedicineRequest("med_amox", "Trùng",
                null, "Viên", "Viên", BigDecimal.TEN, null, null)));
        verify(medicineRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_canDeactivateButNeverTouchesStock() {
        loginAs(ADMIN, RoleCode.ADMIN);
        Medicine medicine = medicine(1L, "Clavamox", "15000", 20);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(medicine));

        MedicineResponse response = service.update(1L, new UpdateMedicineRequest("Clavamox 500", null, "Viên nén", "Viên",
                new BigDecimal("18000"), null, false));

        assertThat(response.active()).isFalse();
        assertThat(response.salePrice()).isEqualByComparingTo("18000");
        assertThat(response.stockQuantity()).isEqualTo(20);
        assertThat(response.minStockAlert()).isEqualTo(10);   // bỏ trống = giữ nguyên
        verify(medicineRepository, never()).addStock(anyLong(), anyInt());
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void getById_inactiveMedicineIsHiddenFromNonAdmins() {
        Medicine inactive = medicine(1L, "Thuốc cũ", "1000", 5);
        inactive.setActive(false);
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(inactive));

        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        assertError(ErrorCode.MEDICINE_NOT_FOUND, () -> service.getById(1L));

        loginAs(ADMIN, RoleCode.ADMIN);
        assertThat(service.getById(1L).active()).isFalse();
    }

    @Test
    void getById_unknownMedicine() {
        loginAs(ADMIN, RoleCode.ADMIN);
        when(medicineRepository.findById(eq(404L))).thenReturn(Optional.empty());

        assertError(ErrorCode.MEDICINE_NOT_FOUND, () -> service.getById(404L));
    }
}
