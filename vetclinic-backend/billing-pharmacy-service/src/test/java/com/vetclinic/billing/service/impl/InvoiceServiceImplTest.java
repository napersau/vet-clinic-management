package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.config.BillingProperties;
import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.DiscountRequest;
import com.vetclinic.billing.dto.response.BillableServiceResponse;
import com.vetclinic.billing.dto.response.InvoiceResponse;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.event.PaymentAndDispenseCompletedEvent;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.vetclinic.billing.testsupport.TestData.CLOCK;
import static com.vetclinic.billing.testsupport.TestData.DOCTOR;
import static com.vetclinic.billing.testsupport.TestData.RECEPTIONIST;
import static com.vetclinic.billing.testsupport.TestData.assertError;
import static com.vetclinic.billing.testsupport.TestData.item;
import static com.vetclinic.billing.testsupport.TestData.loginAs;
import static com.vetclinic.billing.testsupport.TestData.medicine;
import static com.vetclinic.billing.testsupport.TestData.prescription;
import static com.vetclinic.billing.testsupport.TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PrescriptionRepository prescriptionRepository;
    @Mock
    private VisitLookup visitLookup;
    @Mock
    private NamedLockService lockService;
    @Mock
    private ApplicationEventPublisher publisher;

    private static final java.time.Instant EVENT_AT = java.time.Instant.parse("2026-10-30T02:00:00Z");

    private InvoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().when(lockService.runLocked(anyCollection(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        lenient().when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> {
            Invoice invoice = inv.getArgument(0);
            if (invoice.getId() == null) {
                invoice.setId(12L);
            }
            return invoice;
        });
        // VAT 10% cho dịch vụ, 5% cho thuốc
        BillingProperties properties = new BillingProperties(
                new BillingProperties.Vat(new BigDecimal("0.10"), new BigDecimal("0.05")), "Asia/Ho_Chi_Minh");
        service = new InvoiceServiceImpl(invoiceRepository, prescriptionRepository, visitLookup, new BillingAssembler(),
                properties, lockService, publisher, CLOCK, tm);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static List<BillableServiceResponse> services() {
        return List.of(
                new BillableServiceResponse(1L, "EXAM_GEN", "Khám tổng quát", "EXAMINATION", 1,
                        new BigDecimal("100000"), new BigDecimal("100000")),
                new BillableServiceResponse(2L, "LAB_CBC", "Xét nghiệm máu", "LABORATORY", 1,
                        new BigDecimal("150000"), new BigDecimal("150000")));
    }

    private static Invoice pendingInvoice() {
        Invoice invoice = Invoice.builder().id(40L).invoiceCode("INV-2026-00040").visitId(7L)
                .totalServiceAmount(BigDecimal.ZERO).totalMedicineAmount(BigDecimal.ZERO).totalVatAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO).build();
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 1L, "Khám", 1, new BigDecimal("100000"), BigDecimal.ZERO));
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.MEDICINE, 5L, "Vaccine", 1, new BigDecimal("180000"), BigDecimal.ZERO));
        InvoiceCalculator.recalculate(invoice);   // tổng viện phí 280000
        return invoice;
    }

    // ================================================================== lập hóa đơn từ sự kiện

    @Test
    void createForVisit_buildsSeparatedServiceAndMedicineLinesWithVat() {
        Prescription prescription = prescription(3L, 7L, PrescriptionStatus.PRESCRIBED,
                item(1L, medicine(9L, "Clavamox", "15000", 200), 3));
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.of(prescription));

        service.createForVisit(7L, EVENT_AT);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        Invoice invoice = saved.getValue();
        assertThat(invoice.getInvoiceCode()).isEqualTo("INV-2026-00012");   // năm lập + id
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(invoice.getCashierId()).isNull();                          // gán lúc xác nhận thanh toán
        assertThat(invoice.getPrescriptionId()).isEqualTo(3L);
        assertThat(invoice.getItems()).extracting(i -> i.getItemType())
                .containsExactly(InvoiceItemType.SERVICE, InvoiceItemType.SERVICE, InvoiceItemType.MEDICINE);
        assertThat(invoice.getTotalServiceAmount()).isEqualByComparingTo("250000");
        assertThat(invoice.getTotalMedicineAmount()).isEqualByComparingTo("45000");
        assertThat(invoice.getTotalVatAmount()).isEqualByComparingTo("27250");   // 10% * 250000 + 5% * 45000
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("322250");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("0");
    }

    @Test
    void createForVisit_visitWithoutPrescriptionHasOnlyServiceLines() {
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());

        service.createForVisit(7L, EVENT_AT);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPrescriptionId()).isNull();
        assertThat(saved.getValue().getTotalMedicineAmount()).isEqualByComparingTo("0");
    }

    @Test
    void createForVisit_ignoresACancelledPrescription() {
        Prescription cancelled = prescription(3L, 7L, PrescriptionStatus.CANCELLED,
                item(1L, medicine(9L, "Clavamox", "15000", 200), 3));
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.of(cancelled));

        service.createForVisit(7L, EVENT_AT);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPrescriptionId()).isNull();
        assertThat(saved.getValue().getTotalMedicineAmount()).isEqualByComparingTo("0");
    }

    @Test
    void createForVisit_redeliveredEventDoesNothing() {
        when(invoiceRepository.findByVisitId(7L)).thenReturn(Optional.of(pendingInvoice()));

        service.createForVisit(7L, EVENT_AT);

        verifyNoInteractions(visitLookup);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void createForVisit_losingTheRaceOnTheUniqueVisitIdIsNotAnError() {
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenThrow(new DataIntegrityViolationException("uk_visit"));

        assertThatCode(() -> service.createForVisit(7L, EVENT_AT)).doesNotThrowAnyException();
    }

    @Test
    void generateForVisit_refusesAVisitTheDoctorHasNotClosed() {
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.IN_PROGRESS, DOCTOR));

        assertError(ErrorCode.VISIT_NOT_READY_FOR_INVOICE, () -> service.generateForVisit(7L));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void generateForVisit_createsTheMissingInvoiceOfAClosedVisit() {
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        Invoice created = pendingInvoice();
        when(invoiceRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.of(created));
        when(visitLookup.tryFind(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));

        InvoiceResponse response = service.generateForVisit(7L);

        assertThat(response.id()).isEqualTo(40L);
        verify(invoiceRepository).saveAndFlush(any(Invoice.class));
    }

    // ================================================================== chiết khấu

    @Test
    void applyDiscount_updatesTheFinalAmount() {
        Invoice invoice = pendingInvoice();
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.applyDiscount(40L, new DiscountRequest(new BigDecimal("30000"), "Khách quen"));

        assertThat(response.discountAmount()).isEqualByComparingTo("30000");
        assertThat(response.finalAmount()).isEqualByComparingTo("250000");   // 280000 - 30000
        assertThat(response.notes()).isEqualTo("Khách quen");
    }

    @Test
    void applyDiscount_equalToTheTotalIsAllowed_butAboveIsRefused_EX14() {
        Invoice invoice = pendingInvoice();
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        assertError(ErrorCode.DISCOUNT_EXCEEDS_TOTAL,
                () -> service.applyDiscount(40L, new DiscountRequest(new BigDecimal("280000.01"), null)));
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("0");   // bị chặn thì không đổi gì

        InvoiceResponse free = service.applyDiscount(40L, new DiscountRequest(new BigDecimal("280000"), null));
        assertThat(free.finalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void applyDiscount_toAPaidInvoiceIsRefused() {
        Invoice invoice = pendingInvoice();
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        assertError(ErrorCode.INVOICE_ALREADY_PAID,
                () -> service.applyDiscount(40L, new DiscountRequest(BigDecimal.TEN, null)));
    }

    @Test
    void applyDiscount_unknownInvoice() {
        when(invoiceRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.INVOICE_NOT_FOUND, () -> service.applyDiscount(99L, new DiscountRequest(BigDecimal.ONE, null)));
    }

    // ================================================================== hủy hóa đơn / mở lại đợt khám

    private static Invoice cancelledInvoice(java.time.Instant cancelledAt) {
        Invoice invoice = pendingInvoice();
        invoice.setPaymentStatus(PaymentStatus.CANCELLED);
        invoice.setDiscountAmount(new BigDecimal("50000"));
        invoice.setUpdatedAt(cancelledAt);
        return invoice;
    }

    @Test
    void cancel_pendingInvoiceKeepsTheReasonAndFreesThePrescriptionLink() {
        Invoice invoice = pendingInvoice();
        invoice.setPrescriptionId(3L);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.cancel(40L, "  Nhập nhầm dịch vụ ");

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(invoice.getPrescriptionId()).isNull();
        assertThat(invoice.getNotes()).contains("Nhập nhầm dịch vụ");
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void cancel_paidInvoiceIsRefused() {
        Invoice invoice = pendingInvoice();
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        assertError(ErrorCode.INVOICE_ALREADY_PAID, () -> service.cancel(40L, "x"));
    }

    @Test
    void cancelForReopen_cancelsPendingAndIsIdempotentOtherwise() {
        Invoice pending = pendingInvoice();
        when(invoiceRepository.findByVisitIdForUpdate(7L)).thenReturn(Optional.of(pending));
        service.cancelForReopen(7L);
        assertThat(pending.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(pending.getNotes()).contains("mở lại");

        // Gọi lại (clinical thử lại sau lỗi giữa chừng) và đợt khám chưa có hóa đơn đều là no-op
        service.cancelForReopen(7L);
        when(invoiceRepository.findByVisitIdForUpdate(8L)).thenReturn(Optional.empty());
        service.cancelForReopen(8L);
        verify(invoiceRepository, org.mockito.Mockito.times(1)).saveAndFlush(any(Invoice.class));
    }

    @Test
    void cancelForReopen_refusesWhenTheCustomerAlreadyPaid() {
        Invoice paid = pendingInvoice();
        paid.setPaymentStatus(PaymentStatus.PAID);
        when(invoiceRepository.findByVisitIdForUpdate(7L)).thenReturn(Optional.of(paid));

        assertError(ErrorCode.INVOICE_ALREADY_PAID, () -> service.cancelForReopen(7L));
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createForVisit_afterReopenAndReclose_revivesTheCancelledInvoiceWithFreshLines() {
        Invoice cancelled = cancelledInvoice(java.time.Instant.parse("2026-10-30T01:00:00Z"));
        cancelled.setCashierId(9L);
        when(invoiceRepository.findByVisitId(7L)).thenReturn(Optional.of(cancelled));
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());

        service.createForVisit(7L, EVENT_AT);   // sự kiện chốt lại (02:00) xảy ra SAU lúc hủy (01:00)

        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(cancelled.getInvoiceCode()).isEqualTo("INV-2026-00040");   // giữ mã cũ
        assertThat(cancelled.getDiscountAmount()).isEqualByComparingTo("0");   // chiết khấu cũ không mang sang
        assertThat(cancelled.getCashierId()).isNull();
        assertThat(cancelled.getItems()).hasSize(2);                           // dòng cũ bị thay bằng dòng mới
        assertThat(cancelled.getTotalServiceAmount()).isEqualByComparingTo("250000").isNotEqualTo(BigDecimal.ZERO);
        assertThat(cancelled.getFinalAmount()).isEqualByComparingTo("275000");   // 250000 + 10% VAT
    }

    @Test
    void createForVisit_aStaleClosedEventDoesNotReviveACancelledInvoice() {
        // Sự kiện chốt ca CŨ (00:30) bị RabbitMQ giao lại sau khi hóa đơn đã hủy (01:00) nhưng chưa chốt lại
        Invoice cancelled = cancelledInvoice(java.time.Instant.parse("2026-10-30T01:00:00Z"));
        when(invoiceRepository.findByVisitId(7L)).thenReturn(Optional.of(cancelled));

        service.createForVisit(7L, java.time.Instant.parse("2026-10-30T00:30:00Z"));

        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verifyNoInteractions(visitLookup);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void generateForVisit_byAStaffMemberRevivesACancelledInvoiceEvenWithoutAnEvent() {
        Invoice cancelled = cancelledInvoice(java.time.Instant.parse("2026-10-30T01:00:00Z"));
        when(visitLookup.require(7L)).thenReturn(visit(7L, VisitStatus.WAITING_PAYMENT, DOCTOR));
        when(invoiceRepository.findByVisitId(7L)).thenReturn(Optional.of(cancelled));
        when(visitLookup.billableServices(7L)).thenReturn(services());
        when(prescriptionRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.empty());
        when(invoiceRepository.findWithItemsByVisitId(7L)).thenReturn(Optional.of(cancelled));
        when(visitLookup.tryFind(7L)).thenReturn(null);

        service.generateForVisit(7L);

        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ================================================================== thanh toán

    @Test
    void confirmPayment_withoutPrescriptionPaysAndCompletesTheVisitAtOnce() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Invoice invoice = pendingInvoice();
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.confirmPayment(40L);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.cashierId()).isEqualTo(RECEPTIONIST);
        assertThat(response.paidAt()).isEqualTo(CLOCK.instant());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue()).isInstanceOfSatisfying(PaymentAndDispenseCompletedEvent.class, e -> {
            assertThat(e.visitId()).isEqualTo(7L);
            assertThat(e.invoiceId()).isEqualTo(40L);
            assertThat(e.prescriptionId()).isNull();
            assertThat(e.actorUserId()).isEqualTo(RECEPTIONIST);   // clinical ghi vào lịch sử trạng thái
        });
    }

    @Test
    void confirmPayment_withAnUndispensedPrescriptionWaitsForDispensing() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Invoice invoice = pendingInvoice();
        invoice.setPrescriptionId(3L);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));
        when(prescriptionRepository.findById(3L))
                .thenReturn(Optional.of(prescription(3L, 7L, PrescriptionStatus.PRESCRIBED)));

        InvoiceResponse response = service.confirmPayment(40L);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(publisher, never()).publishEvent(any());   // BR-28: phát thuốc xong mới hoàn tất ca
    }

    @Test
    void confirmPayment_twiceIsRefusedAndPublishesNothingMore() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Invoice invoice = pendingInvoice();
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        assertError(ErrorCode.INVOICE_ALREADY_PAID, () -> service.confirmPayment(40L));
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void confirmPayment_cancelledInvoiceIsRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Invoice invoice = pendingInvoice();
        invoice.setPaymentStatus(PaymentStatus.CANCELLED);
        when(invoiceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(invoice));

        assertError(ErrorCode.INVALID_INVOICE_STATUS, () -> service.confirmPayment(40L));
    }
}
