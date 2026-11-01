package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.InventoryTransactionType;
import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.PrescriptionItemRequest;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.repository.InventoryTransactionRepository;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.StockAlertPublisher;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.event.PaymentAndDispenseCompletedEvent;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.vetclinic.billing.testsupport.TestData.CLOCK;
import static com.vetclinic.billing.testsupport.TestData.DOCTOR;
import static com.vetclinic.billing.testsupport.TestData.OTHER_DOCTOR;
import static com.vetclinic.billing.testsupport.TestData.OWNER;
import static com.vetclinic.billing.testsupport.TestData.RECEPTIONIST;
import static com.vetclinic.billing.testsupport.TestData.assertError;
import static com.vetclinic.billing.testsupport.TestData.item;
import static com.vetclinic.billing.testsupport.TestData.loginAs;
import static com.vetclinic.billing.testsupport.TestData.medicine;
import static com.vetclinic.billing.testsupport.TestData.prescription;
import static com.vetclinic.billing.testsupport.TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InventoryTransactionRepository inventoryRepository;
    @Mock
    private VisitLookup visitLookup;
    @Mock
    private StockAlertPublisher stockAlerts;
    @Mock
    private NamedLockService lockService;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private EntityManager entityManager;

    private PrescriptionServiceImpl service;

    /** "CSDL" giả của kho: deductStock trừ thật vào đây và refresh() đọc lại từ đây, mô phỏng UPDATE nguyên tử. */
    private final Map<Long, Integer> stockDb = new HashMap<>();

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(lockService.runLocked(anyCollection(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(medicineRepository.deductStock(anyLong(), anyInt())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            int quantity = inv.getArgument(1);
            if (stockDb.get(id) < quantity) {
                return 0;
            }
            stockDb.merge(id, -quantity, Integer::sum);
            return 1;
        });
        lenient().doAnswer(inv -> {
            Medicine medicine = inv.getArgument(0);
            medicine.setStockQuantity(stockDb.get(medicine.getId()));
            return null;
        }).when(entityManager).refresh(any(Medicine.class));
        lenient().when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PrescriptionServiceImpl(prescriptionRepository, medicineRepository, invoiceRepository,
                inventoryRepository, visitLookup, new BillingAssembler(), stockAlerts, lockService, publisher,
                entityManager, CLOCK, tm);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static PrescriptionItemRequest line(long medicineId, int quantity) {
        return new PrescriptionItemRequest(medicineId, quantity, "1 viên", "2 lần/ngày", 5, null);
    }

    private static PrescriptionRequest request(long visitId, PrescriptionItemRequest... lines) {
        return new PrescriptionRequest(visitId, "Cho ăn nhẹ", List.of(lines));
    }

    private void doctorOnInProgressVisit(long visitId) {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        when(visitLookup.require(visitId)).thenReturn(visit(visitId, VisitStatus.IN_PROGRESS, DOCTOR));
    }

    // ================================================================== kê đơn

    @Test
    void create_snapshotsCurrentPriceAndDoesNotTouchStock() {
        doctorOnInProgressVisit(7L);
        Medicine amox = medicine(1L, "Clavamox", "15000", 200);
        Medicine drontal = medicine(2L, "Drontal", "45000", 100);
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(medicineRepository.findAllById(any())).thenReturn(List.of(amox, drontal));

        PrescriptionResponse response = service.create(request(7L, line(1L, 10), line(2L, 2)));

        assertThat(response.status()).isEqualTo(PrescriptionStatus.PRESCRIBED);
        assertThat(response.visitCode()).isEqualTo("VIS-2026-7");
        assertThat(response.items()).extracting(i -> i.unitPrice().intValue()).containsExactly(15000, 45000);
        assertThat(response.items()).extracting(i -> i.subtotal().intValue()).containsExactly(150000, 90000);
        assertThat(response.totalAmount()).isEqualByComparingTo("240000");
        // BR-29: kê đơn chưa được trừ kho
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    void create_isRefusedWhenTheVisitIsNotBeingExamined() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        assertError(ErrorCode.PRESCRIPTION_LOCKED, () -> service.create(request(7L, line(1L, 1))));
        verify(prescriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_isRefusedForADoctorWhoDoesNotOwnTheVisit() {
        loginAs(OTHER_DOCTOR, RoleCode.VETERINARIAN);
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.IN_PROGRESS, DOCTOR));

        assertError(ErrorCode.VISIT_NOT_ASSIGNED_TO_YOU, () -> service.create(request(7L, line(1L, 1))));
    }

    @Test
    void create_requiresAVisit() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);

        assertError(ErrorCode.INVALID_INPUT_DATA,
                () -> service.create(new PrescriptionRequest(null, null, List.of(line(1L, 1)))));
    }

    @Test
    void create_quantityAboveAvailableStockIsRefused_EX13() {
        doctorOnInProgressVisit(7L);
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(medicineRepository.findAllById(any())).thenReturn(List.of(medicine(1L, "Clavamox", "15000", 6)));

        assertError(ErrorCode.INSUFFICIENT_STOCK, () -> service.create(request(7L, line(1L, 10))));
        verify(prescriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_unknownOrInactiveMedicineIsRefused() {
        doctorOnInProgressVisit(7L);
        Medicine inactive = medicine(1L, "Thuốc cũ", "1000", 50);
        inactive.setActive(false);
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(medicineRepository.findAllById(any())).thenReturn(List.of(inactive));

        assertError(ErrorCode.MEDICINE_NOT_FOUND, () -> service.create(request(7L, line(1L, 1))));
        assertError(ErrorCode.MEDICINE_NOT_FOUND, () -> service.create(request(7L, line(99L, 1))));
    }

    @Test
    void create_sameMedicineTwiceIsRefused() {
        doctorOnInProgressVisit(7L);

        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.create(request(7L, line(1L, 1), line(1L, 2))));
    }

    @Test
    void create_isRefusedOnceTheInvoiceExists() {
        // Bác sĩ chậm tay: ca đã chốt và hóa đơn đã lập thì không còn được chen đơn thuốc vào
        doctorOnInProgressVisit(7L);
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(true);

        assertError(ErrorCode.PRESCRIPTION_LOCKED, () -> service.create(request(7L, line(1L, 1))));
        verify(prescriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_secondPrescriptionForTheSameVisitIsRefused_BR18() {
        doctorOnInProgressVisit(7L);
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsByVisitId(7L))
                .thenReturn(Optional.of(prescription(3L, 7L, PrescriptionStatus.PRESCRIBED)));

        assertError(ErrorCode.PRESCRIPTION_ALREADY_EXISTS, () -> service.create(request(7L, line(1L, 1))));
    }

    @Test
    void create_reusesACancelledPrescriptionOfTheSameVisit() {
        doctorOnInProgressVisit(7L);
        Medicine amox = medicine(1L, "Clavamox", "15000", 200);
        Prescription cancelled = prescription(3L, 7L, PrescriptionStatus.CANCELLED, item(1L, medicine(2L, "Cũ", "1000", 5), 1));
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.of(cancelled));
        when(medicineRepository.findAllById(any())).thenReturn(List.of(amox));

        PrescriptionResponse response = service.create(request(7L, line(1L, 4)));

        assertThat(response.id()).isEqualTo(3L);   // visit_id UNIQUE nên phải dùng lại đúng dòng cũ
        assertThat(response.status()).isEqualTo(PrescriptionStatus.PRESCRIBED);
        assertThat(response.items()).hasSize(1).first().satisfies(i -> assertThat(i.medicineId()).isEqualTo(1L));
    }

    @Test
    void update_replacesAllLinesWhileTheVisitIsStillBeingExamined() {
        doctorOnInProgressVisit(7L);
        Prescription existing = prescription(3L, 7L, PrescriptionStatus.PRESCRIBED, item(1L, medicine(1L, "Clavamox", "15000", 200), 2));
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(medicineRepository.findAllById(any())).thenReturn(List.of(medicine(2L, "Drontal", "45000", 100)));

        PrescriptionResponse response = service.update(3L, request(7L, line(2L, 1)));

        assertThat(response.items()).extracting(i -> i.medicineId()).containsExactly(2L);
        assertThat(response.totalAmount()).isEqualByComparingTo("45000");
    }

    // ================================================================== xuất thuốc

    private Prescription twoItemPrescription(PrescriptionStatus status) {
        Medicine med5 = medicine(5L, "Vaccine", "180000", 50);
        Medicine med2 = medicine(2L, "Drontal", "45000", 100);
        stockDb.put(5L, 50);
        stockDb.put(2L, 100);
        // Cố ý đặt thuốc id lớn trước để kiểm tra việc sắp xếp
        return prescription(3L, 7L, status, item(1L, med5, 1), item(2L, med2, 4));
    }

    private Invoice invoice(PaymentStatus status) {
        return Invoice.builder().id(40L).visitId(7L).prescriptionId(3L).paymentStatus(status)
                .totalServiceAmount(BigDecimal.ZERO).totalMedicineAmount(BigDecimal.ZERO).totalVatAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO).build();
    }

    private void stubReadAfterDispense(Prescription prescription, Invoice invoice) {
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        lenient().when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.of(invoice));
        lenient().when(visitLookup.tryFind(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));
    }

    @Test
    void dispense_deductsStockInMedicineIdOrder_logsAndCompletesTheVisit() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        Invoice paid = invoice(PaymentStatus.PAID);
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.of(paid));
        stubReadAfterDispense(prescription, paid);

        PrescriptionResponse response = service.dispense(3L);

        InOrder order = inOrder(medicineRepository);
        order.verify(medicineRepository).deductStock(2L, 4);   // id nhỏ trước dù đơn liệt kê thuốc 5 trước
        order.verify(medicineRepository).deductStock(5L, 1);
        assertThat(stockDb).containsEntry(2L, 96).containsEntry(5L, 49);

        ArgumentCaptor<InventoryTransaction> logs = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryRepository, org.mockito.Mockito.times(2)).save(logs.capture());
        assertThat(logs.getAllValues()).allSatisfy(l -> {
            assertThat(l.getTransactionType()).isEqualTo(InventoryTransactionType.EXPORT_PRESCRIPTION);
            assertThat(l.getReferenceId()).isEqualTo(3L);
            assertThat(l.getCreatedBy()).isEqualTo(RECEPTIONIST);
        });
        assertThat(logs.getAllValues()).extracting(InventoryTransaction::getBalanceAfter).containsExactly(96, 49);

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(prescription.getDispensedBy()).isEqualTo(RECEPTIONIST);
        assertThat(prescription.getDispensedAt()).isEqualTo(CLOCK.instant());
        assertThat(response.status()).isEqualTo(PrescriptionStatus.DISPENSED);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue()).isInstanceOfSatisfying(PaymentAndDispenseCompletedEvent.class, e -> {
            assertThat(e.visitId()).isEqualTo(7L);
            assertThat(e.invoiceId()).isEqualTo(40L);
            assertThat(e.prescriptionId()).isEqualTo(3L);
            assertThat(e.actorUserId()).isEqualTo(RECEPTIONIST);
        });
        verify(stockAlerts, org.mockito.Mockito.times(2)).publishIfLow(any(Medicine.class), org.mockito.ArgumentMatchers.eq(RECEPTIONIST));
    }

    @Test
    void dispense_isRefusedBeforeTheInvoiceIsPaid_BR28() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.of(invoice(PaymentStatus.PENDING)));

        assertError(ErrorCode.INVOICE_NOT_PAID, () -> service.dispense(3L));
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void dispense_aSecondTimeIsRefused_EX16() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(prescriptionRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.DISPENSED)));

        assertError(ErrorCode.PRESCRIPTION_ALREADY_DISPENSED, () -> service.dispense(3L));
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void dispense_cancelledPrescriptionCannotBeDispensed() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(prescriptionRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.CANCELLED)));

        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.dispense(3L));
    }

    @Test
    void dispense_stockShortAtTheLastMomentFailsTheWholeTransaction_EX15() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        stockDb.put(5L, 0);   // thuốc 5 vừa bị ca cấp cứu khác lấy hết; thuốc 2 (xử lý trước) vẫn đủ
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.of(invoice(PaymentStatus.PAID)));

        assertError(ErrorCode.INSUFFICIENT_STOCK, () -> service.dispense(3L));

        // Ném ngoại lệ thì TransactionTemplate rollback: việc trừ thuốc 2 đã làm trong transaction cũng bị hoàn tác.
        // Ở đây kiểm tra phần thuộc trách nhiệm của service: không chuyển trạng thái, không phát sự kiện hoàn tất.
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PRESCRIBED);
        verify(publisher, never()).publishEvent(any());
    }

    // ================================================================== hủy đơn

    @Test
    void cancel_byDoctorBeforeInvoiceExists() {
        doctorOnInProgressVisit(7L);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(false);
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.empty());
        lenient().when(visitLookup.tryFind(7L)).thenReturn(null);

        PrescriptionResponse response = service.cancel(3L);

        assertThat(response.status()).isEqualTo(PrescriptionStatus.CANCELLED);
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());   // hủy đơn không biến động kho
    }

    @Test
    void cancel_byDoctorOnceTheInvoiceExistsIsLocked() {
        doctorOnInProgressVisit(7L);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.hasActiveInvoice(7L)).thenReturn(true);

        assertError(ErrorCode.PRESCRIPTION_LOCKED, () -> service.cancel(3L));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PRESCRIBED);
    }

    @Test
    void cancel_byCashier_removesMedicineLinesFromThePendingInvoiceAndCapsTheDiscount() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        Invoice invoice = invoice(PaymentStatus.PENDING);
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 1L, "Khám", 1, new BigDecimal("100000"), BigDecimal.ZERO));
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.MEDICINE, 5L, "Vaccine", 1, new BigDecimal("180000"), BigDecimal.ZERO));
        invoice.setDiscountAmount(new BigDecimal("150000"));   // hợp lệ khi còn tiền thuốc (tổng 280000)
        InvoiceCalculator.recalculate(invoice);
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("130000");

        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByVisitIdForUpdate(7L)).thenReturn(Optional.of(invoice));
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.empty());

        service.cancel(3L);

        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(invoice.getItems()).extracting(i -> i.getItemType()).containsExactly(InvoiceItemType.SERVICE);
        assertThat(invoice.getPrescriptionId()).isNull();
        assertThat(invoice.getTotalMedicineAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("100000");   // kéo về mức trần mới
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cancel_byCashierAfterPaymentIsRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByVisitIdForUpdate(7L)).thenReturn(Optional.of(invoice(PaymentStatus.PAID)));

        assertError(ErrorCode.INVOICE_ALREADY_PAID, () -> service.cancel(3L));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PRESCRIBED);
    }

    @Test
    void cancel_byCashierWithoutAnInvoiceIsRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByVisitIdForUpdate(7L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.cancel(3L));
    }

    // ================================================================== tra cứu

    @Test
    void getById_ownerSeesOwnPetsPrescription() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.empty());
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        PrescriptionResponse response = service.getById(3L);

        assertThat(response.petName()).isEqualTo("Lu");
    }

    @Test
    void getById_ownerOfAnotherPetGetsNotFound() {
        loginAs(OWNER + 1, RoleCode.PET_OWNER);
        when(prescriptionRepository.findWithItemsById(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.PRESCRIBED)));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.empty());
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        assertError(ErrorCode.PRESCRIPTION_NOT_FOUND, () -> service.getById(3L));
    }

    @Test
    void getById_staffStillGetsTheDataWhenClinicalIsDown() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(prescriptionRepository.findWithItemsById(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.PRESCRIBED)));
        when(invoiceRepository.findByPrescriptionId(3L)).thenReturn(Optional.empty());
        when(visitLookup.tryFind(7L)).thenReturn(null);

        PrescriptionResponse response = service.getById(3L);

        assertThat(response.items()).hasSize(2);
        assertThat(response.visitCode()).isNull();   // chỉ thiếu phần làm giàu, không lỗi
    }

    // ================================================================== bổ sung

    @Test
    void update_aCancelledPrescriptionIsRefused() {
        doctorOnInProgressVisit(7L);
        Prescription cancelled = prescription(3L, 7L, PrescriptionStatus.CANCELLED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(cancelled));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(cancelled));

        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.update(3L, request(7L, line(2L, 1))));
    }

    @Test
    void cancel_byDoctorAfterTheVisitWasClosedIsLocked() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        assertError(ErrorCode.PRESCRIPTION_LOCKED, () -> service.cancel(3L));
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.PRESCRIBED);
    }

    @Test
    void cancel_anAlreadyCancelledOrDispensedPrescriptionIsRefused() {
        doctorOnInProgressVisit(7L);
        Prescription cancelled = twoItemPrescription(PrescriptionStatus.CANCELLED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(cancelled));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(cancelled));
        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.cancel(3L));

        Prescription dispensed = prescription(4L, 7L, PrescriptionStatus.DISPENSED);
        when(prescriptionRepository.findById(4L)).thenReturn(Optional.of(dispensed));
        when(prescriptionRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(dispensed));
        assertError(ErrorCode.PRESCRIPTION_ALREADY_DISPENSED, () -> service.cancel(4L));
    }
}
