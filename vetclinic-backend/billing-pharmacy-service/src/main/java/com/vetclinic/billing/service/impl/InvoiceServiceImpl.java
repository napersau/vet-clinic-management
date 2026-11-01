package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.config.BillingProperties;
import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.DiscountRequest;
import com.vetclinic.billing.dto.response.BillableServiceResponse;
import com.vetclinic.billing.dto.response.InvoiceResponse;
import com.vetclinic.billing.dto.response.InvoiceSummaryResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.InvoiceSpecifications;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.BillingLocks;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.InvoiceService;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.event.PaymentAndDispenseCompletedEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Không đánh @Transactional ở mức class vì có hàm gọi Feign: hàm đó chạy Feign ngoài transaction rồi mới mở
 * TransactionTemplate (xem VisitLookup). Phép tính tiền nằm hết ở InvoiceCalculator.
 */
@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoiceRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final VisitLookup visitLookup;
    private final BillingAssembler assembler;
    private final BillingProperties properties;
    private final NamedLockService lockService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository, PrescriptionRepository prescriptionRepository,
                              VisitLookup visitLookup, BillingAssembler assembler, BillingProperties properties,
                              NamedLockService lockService, ApplicationEventPublisher eventPublisher, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this.invoiceRepository = invoiceRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.visitLookup = visitLookup;
        this.assembler = assembler;
        this.properties = properties;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    // ================================================================== lập hóa đơn

    @Override
    public void createForVisit(Long visitId, Instant eventOccurredAt) {
        ensureInvoice(visitId, eventOccurredAt);
    }

    @Override
    public InvoiceResponse generateForVisit(Long visitId) {
        VisitSummaryResponse visit = visitLookup.require(visitId);
        // COMPLETED: ca khách bỏ về (EX-11) đã đóng nhưng hóa đơn nợ vẫn phải lập
        if (visit.status() != VisitStatus.WAITING_PAYMENT && visit.status() != VisitStatus.COMPLETED) {
            throw new AppException(ErrorCode.VISIT_NOT_READY_FOR_INVOICE);
        }
        ensureInvoice(visitId, null);   // thao tác thủ công của người có quyền: được dựng lại hóa đơn đã hủy
        return getByVisit(visitId);
    }

    /** Idempotent: nhận trùng sự kiện, hoặc hai nơi cùng lập, đều chỉ ra đúng một hóa đơn (visit_id UNIQUE). */
    private void ensureInvoice(Long visitId, Instant requestedAt) {
        Invoice existing = invoiceRepository.findByVisitId(visitId).orElse(null);
        if (existing != null && !mayRevive(existing, requestedAt)) {
            log.info("Đợt khám {} đã có hóa đơn, bỏ qua", visitId);
            return;
        }
        List<BillableServiceResponse> services = visitLookup.billableServices(visitId);   // Feign, ngoài transaction
        try {
            lockService.runLocked(List.of(BillingLocks.visit(visitId)), () -> tx.execute(s -> persistInvoice(visitId, services, requestedAt)));
        } catch (DataIntegrityViolationException e) {
            // Chốt chặn cuối nếu khóa tên không giữ được (UNIQUE visit_id)
            log.info("Đợt khám {} vừa được lập hóa đơn bởi tiến trình khác, bỏ qua", visitId);
        }
    }

    /**
     * Hóa đơn đã HỦY được dựng lại khi đợt khám chốt lại sau khi mở lại. requestedAt == null là thao tác thủ công; ngược lại
     * sự kiện phải xảy ra sau lúc hóa đơn bị hủy (updatedAt), nếu không đó là sự kiện chốt ca cũ bị giao lại.
     */
    private static boolean mayRevive(Invoice invoice, Instant requestedAt) {
        return invoice.getPaymentStatus() == PaymentStatus.CANCELLED
                && (requestedAt == null || requestedAt.isAfter(invoice.getUpdatedAt()));
    }

    private Object persistInvoice(Long visitId, List<BillableServiceResponse> services, Instant requestedAt) {
        Invoice existing = invoiceRepository.findByVisitId(visitId).orElse(null);
        if (existing != null && !mayRevive(existing, requestedAt)) {
            return null;
        }
        Prescription prescription = prescriptionRepository.findWithItemsByVisitId(visitId)
                .filter(p -> p.getStatus() == PrescriptionStatus.PRESCRIBED).orElse(null);

        Invoice invoice;
        if (existing != null) {
            // Dựng lại hóa đơn đã hủy: giữ mã và dòng bản ghi (visit_id UNIQUE), làm mới toàn bộ nội dung
            invoice = existing;
            invoice.getItems().clear();
            invoice.setPaymentStatus(PaymentStatus.PENDING);
            invoice.setDiscountAmount(BigDecimal.ZERO);
            invoice.setPaidAt(null);
            invoice.setCashierId(null);
            invoice.setPrescriptionId(prescription == null ? null : prescription.getId());
        } else {
            invoice = Invoice.builder()
                    .invoiceCode("TMP-" + UUID.randomUUID())   // thay bằng mã thật ngay khi có id, cùng transaction
                    .visitId(visitId)
                    .prescriptionId(prescription == null ? null : prescription.getId())
                    .totalServiceAmount(BigDecimal.ZERO)
                    .totalMedicineAmount(BigDecimal.ZERO)
                    .totalVatAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(BigDecimal.ZERO)
                    .build();
        }
        BigDecimal serviceRate = properties.vat().serviceRate();
        BigDecimal medicineRate = properties.vat().medicineRate();
        for (BillableServiceResponse service : services) {
            invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, service.serviceId(), service.name(),
                    service.quantity(), service.unitPrice(), serviceRate));
        }
        if (prescription != null) {
            for (PrescriptionItem item : prescription.getItems()) {
                invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.MEDICINE, item.getMedicine().getId(),
                        item.getMedicine().getName(), item.getQuantity(), item.getUnitPrice(), medicineRate));
            }
        }
        InvoiceCalculator.recalculate(invoice);

        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        if (existing == null) {
            saved.setInvoiceCode(invoiceCode(saved.getId()));
        }
        return null;
    }

    /** INV-yyyy-NNNNN (FR-58): năm lập + id nên luôn duy nhất, không cần sinh số rồi thử lại khi trùng. */
    private String invoiceCode(Long id) {
        return "INV-" + LocalDate.now(clock).getYear() + "-" + String.format("%05d", id);
    }

    // ================================================================== tra cứu

    @Override
    public PageResponse<InvoiceSummaryResponse> search(PaymentStatus status, String code, Long visitId,
                                                       LocalDate fromDate, LocalDate toDate, boolean ascending,
                                                       int page, int size) {
        ZoneId zone = properties.zoneId();
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(zone).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(zone).toInstant();
        Sort sort = ascending ? Sort.by("createdAt").ascending().and(Sort.by("id").ascending())
                : Sort.by("createdAt").descending().and(Sort.by("id").descending());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        Page<InvoiceSummaryResponse> result = readTx.execute(s -> invoiceRepository
                .findAll(InvoiceSpecifications.filter(status, code, visitId, from, to), pageable)
                .map(assembler::toSummary));

        Map<Long, VisitSummaryResponse> visits = visitLookup.tryFindAll(
                result.getContent().stream().map(InvoiceSummaryResponse::visitId).collect(Collectors.toSet()));
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), item -> assembler.withVisit(item, visits.get(item.visitId())));
    }

    @Override
    public InvoiceResponse getById(Long id) {
        InvoiceResponse response = readTx.execute(s -> assembler.toResponse(invoiceRepository.findWithItemsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND))));
        return assembler.withVisit(response, visitLookup.tryFind(response.visitId()));
    }

    @Override
    public InvoiceResponse getByVisit(Long visitId) {
        InvoiceResponse response = readTx.execute(s -> assembler.toResponse(invoiceRepository.findWithItemsByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND))));
        return assembler.withVisit(response, visitLookup.tryFind(visitId));
    }

    // ================================================================== chiết khấu & thanh toán

    @Override
    public InvoiceResponse applyDiscount(Long id, DiscountRequest request) {
        InvoiceResponse response = tx.execute(s -> {
            Invoice invoice = lockPending(id);
            BigDecimal discount = InvoiceCalculator.money(request.discountAmount());
            if (discount.compareTo(InvoiceCalculator.gross(invoice)) > 0) {
                throw new AppException(ErrorCode.DISCOUNT_EXCEEDS_TOTAL);
            }
            invoice.setDiscountAmount(discount);
            if (request.notes() != null && !request.notes().isBlank()) {
                invoice.setNotes(request.notes().trim());
            }
            InvoiceCalculator.recalculate(invoice);
            return assembler.toResponse(invoiceRepository.saveAndFlush(invoice));
        });
        return assembler.withVisit(response, visitLookup.tryFind(response.visitId()));
    }

    @Override
    public InvoiceResponse confirmPayment(Long id) {
        Long cashierId = SecurityUtils.getCurrentUserId();
        InvoiceResponse response = tx.execute(s -> {
            Invoice invoice = lockPending(id);
            invoice.setPaymentStatus(PaymentStatus.PAID);
            invoice.setPaidAt(clock.instant());
            invoice.setCashierId(cashierId);

            // Còn đơn thuốc chưa phát thì ca chưa xong (BR-28): chờ UC-36. Không thì hoàn tất ngay (FR-60).
            boolean awaitingDispense = invoice.getPrescriptionId() != null && prescriptionRepository
                    .findById(invoice.getPrescriptionId()).filter(p -> p.getStatus() == PrescriptionStatus.PRESCRIBED).isPresent();
            if (!awaitingDispense) {
                eventPublisher.publishEvent(new PaymentAndDispenseCompletedEvent(UUID.randomUUID(), clock.instant(),
                        cashierId, invoice.getVisitId(), invoice.getId(), null));
            }
            return assembler.toResponse(invoiceRepository.saveAndFlush(invoice));
        });
        return assembler.withVisit(response, visitLookup.tryFind(response.visitId()));
    }

    @Override
    public InvoiceResponse cancel(Long id, String reason) {
        InvoiceResponse response = tx.execute(s -> cancelPending(lockPending(id), "Hủy hóa đơn: " + reason.trim()));
        return assembler.withVisit(response, visitLookup.tryFind(response.visitId()));
    }

    @Override
    public void cancelForReopen(Long visitId) {
        // Cùng khóa với kê đơn và lập hóa đơn của đợt khám này nên không có đơn nào lọt vào giữa chừng
        lockService.runLocked(List.of(BillingLocks.visit(visitId)), () -> tx.execute(s -> {
            Invoice invoice = invoiceRepository.findByVisitIdForUpdate(visitId).orElse(null);
            if (invoice == null || invoice.getPaymentStatus() == PaymentStatus.CANCELLED) {
                return null;
            }
            if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
                throw new AppException(ErrorCode.INVOICE_ALREADY_PAID);
            }
            cancelPending(invoice, "Hủy hóa đơn: đợt khám được mở lại để điều chỉnh chuyên môn");
            return null;
        }));
    }

    /** PENDING -> CANCELLED. Gỡ liên kết đơn thuốc để đơn sửa được lại; hóa đơn dựng lại sẽ liên kết lại với đơn. */
    private InvoiceResponse cancelPending(Invoice invoice, String note) {
        invoice.setPaymentStatus(PaymentStatus.CANCELLED);
        invoice.setPrescriptionId(null);
        invoice.setNotes(invoice.getNotes() == null || invoice.getNotes().isBlank() ? note : invoice.getNotes() + "\n" + note);
        return assembler.toResponse(invoiceRepository.saveAndFlush(invoice));
    }

    /**
     * Khóa dòng hóa đơn rồi mới kiểm tra trạng thái: thanh toán hai lần, chiết khấu chen vào lúc đang thanh toán, hủy
     * đơn thuốc cùng lúc... đều bị tuần tự hóa. Phải gọi trong transaction.
     */
    private Invoice lockPending(Long id) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(ErrorCode.INVOICE_ALREADY_PAID);
        }
        if (invoice.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_INVOICE_STATUS);
        }
        return invoice;
    }
}
