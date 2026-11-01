package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.PrescriptionItemRequest;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
    private VisitLookup visitLookup;
    @Mock
    private NamedLockService lockService;

    private PrescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(lockService.runLocked(anyCollection(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(prescriptionRepository.saveAndFlush(any(Prescription.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new PrescriptionServiceImpl(prescriptionRepository, medicineRepository, visitLookup,
                new BillingAssembler(), lockService, tm);
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

    private static Prescription twoItemPrescription(PrescriptionStatus status) {
        return prescription(3L, 7L, status, item(1L, medicine(5L, "Vaccine", "180000", 50), 1),
                item(2L, medicine(2L, "Drontal", "45000", 100), 4));
    }

    // ================================================================== kê đơn

    @Test
    void create_snapshotsCurrentPriceAndDoesNotTouchStock() {
        doctorOnInProgressVisit(7L);
        Medicine amox = medicine(1L, "Clavamox", "15000", 200);
        Medicine drontal = medicine(2L, "Drontal", "45000", 100);
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(medicineRepository.findAllById(any())).thenReturn(List.of(amox, drontal));

        PrescriptionResponse response = service.create(request(7L, line(1L, 10), line(2L, 2)));

        assertThat(response.status()).isEqualTo(PrescriptionStatus.PRESCRIBED);
        assertThat(response.visitCode()).isEqualTo("VIS-2026-7");
        assertThat(response.items()).extracting(i -> i.unitPrice().intValue()).containsExactly(15000, 45000);
        assertThat(response.items()).extracting(i -> i.subtotal().intValue()).containsExactly(150000, 90000);
        assertThat(response.totalAmount()).isEqualByComparingTo("240000");
        assertThat(response.invoiceId()).isNull();   // chưa có hóa đơn (lập khi chốt ca, NV4b)
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
    void create_secondPrescriptionForTheSameVisitIsRefused_BR18() {
        doctorOnInProgressVisit(7L);
        when(prescriptionRepository.findWithItemsByVisitId(7L))
                .thenReturn(Optional.of(prescription(3L, 7L, PrescriptionStatus.PRESCRIBED)));

        assertError(ErrorCode.PRESCRIPTION_ALREADY_EXISTS, () -> service.create(request(7L, line(1L, 1))));
    }

    @Test
    void create_reusesACancelledPrescriptionOfTheSameVisit() {
        doctorOnInProgressVisit(7L);
        Medicine amox = medicine(1L, "Clavamox", "15000", 200);
        Prescription cancelled = prescription(3L, 7L, PrescriptionStatus.CANCELLED, item(1L, medicine(2L, "Cũ", "1000", 5), 1));
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
        when(medicineRepository.findAllById(any())).thenReturn(List.of(medicine(2L, "Drontal", "45000", 100)));

        PrescriptionResponse response = service.update(3L, request(7L, line(2L, 1)));

        assertThat(response.items()).extracting(i -> i.medicineId()).containsExactly(2L);
        assertThat(response.totalAmount()).isEqualByComparingTo("45000");
    }

    @Test
    void update_aCancelledPrescriptionIsRefused() {
        doctorOnInProgressVisit(7L);
        Prescription cancelled = prescription(3L, 7L, PrescriptionStatus.CANCELLED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(cancelled));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(cancelled));

        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.update(3L, request(7L, line(2L, 1))));
    }

    // ================================================================== hủy đơn

    @Test
    void cancel_byDoctorWhileExamining() {
        doctorOnInProgressVisit(7L);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        lenient().when(visitLookup.tryFind(7L)).thenReturn(null);

        PrescriptionResponse response = service.cancel(3L);

        assertThat(response.status()).isEqualTo(PrescriptionStatus.CANCELLED);
        verify(medicineRepository, never()).deductStock(anyLong(), anyInt());   // hủy đơn không biến động kho
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
    void cancel_byCashierWithoutAnInvoiceIsRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findById(3L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(prescription));

        assertError(ErrorCode.INVALID_PRESCRIPTION_STATUS, () -> service.cancel(3L));
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

    // ================================================================== tra cứu

    @Test
    void getById_ownerSeesOwnPetsPrescription() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Prescription prescription = twoItemPrescription(PrescriptionStatus.PRESCRIBED);
        when(prescriptionRepository.findWithItemsById(3L)).thenReturn(Optional.of(prescription));
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        PrescriptionResponse response = service.getById(3L);

        assertThat(response.petName()).isEqualTo("Lu");
    }

    @Test
    void getById_ownerOfAnotherPetGetsNotFound() {
        loginAs(OWNER + 1, RoleCode.PET_OWNER);
        when(prescriptionRepository.findWithItemsById(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.PRESCRIBED)));
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        assertError(ErrorCode.PRESCRIPTION_NOT_FOUND, () -> service.getById(3L));
    }

    @Test
    void getById_staffStillGetsTheDataWhenClinicalIsDown() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(prescriptionRepository.findWithItemsById(3L))
                .thenReturn(Optional.of(twoItemPrescription(PrescriptionStatus.PRESCRIBED)));
        when(visitLookup.tryFind(7L)).thenReturn(null);

        PrescriptionResponse response = service.getById(3L);

        assertThat(response.items()).hasSize(2);
        assertThat(response.visitCode()).isNull();   // chỉ thiếu phần làm giàu, không lỗi
    }
}
