package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.InventoryTransactionType;
import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.PrescriptionItemRequest;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import com.vetclinic.billing.repository.InventoryTransactionRepository;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.repository.PrescriptionSpecifications;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.BillingLocks;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.PrescriptionService;
import com.vetclinic.billing.service.StockAlertPublisher;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.event.PaymentAndDispenseCompletedEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Không đánh @Transactional ở mức class: các hàm vừa gọi Feign (ngoài transaction) vừa ghi DB (trong transaction) dùng
 * TransactionTemplate để ranh giới transaction tường minh và khóa tên được nhả SAU commit (xem NamedLockService).
 */
@Slf4j
@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    private final InvoiceRepository invoiceRepository;
    private final InventoryTransactionRepository inventoryRepository;
    private final VisitLookup visitLookup;
    private final BillingAssembler assembler;
    private final StockAlertPublisher stockAlerts;
    private final NamedLockService lockService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public PrescriptionServiceImpl(PrescriptionRepository prescriptionRepository, MedicineRepository medicineRepository,
                                   InvoiceRepository invoiceRepository,
                                   InventoryTransactionRepository inventoryRepository, VisitLookup visitLookup,
                                   BillingAssembler assembler, StockAlertPublisher stockAlerts,
                                   NamedLockService lockService, ApplicationEventPublisher eventPublisher,
                                   EntityManager entityManager, Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.invoiceRepository = invoiceRepository;
        this.inventoryRepository = inventoryRepository;
        this.visitLookup = visitLookup;
        this.assembler = assembler;
        this.stockAlerts = stockAlerts;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    // ================================================================== kê đơn

    @Override
    public PrescriptionResponse create(PrescriptionRequest request) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        if (request.visitId() == null) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Phải chọn đợt khám để kê đơn");
        }
        VisitSummaryResponse visit = visitLookup.require(request.visitId());
        assertDoctorMayPrescribe(visit, doctorId);
        PrescriptionResponse created = lockService.runLocked(List.of(BillingLocks.visit(visit.id())),
                () -> tx.execute(s -> persistNew(visit.id(), request)));
        return assembler.withVisit(created, visit);
    }

    private PrescriptionResponse persistNew(Long visitId, PrescriptionRequest request) {
        // Chạy dưới khóa của đợt khám: hóa đơn chưa lập thì mới còn được kê / sửa đơn
        if (invoiceRepository.hasActiveInvoice(visitId)) {
            throw new AppException(ErrorCode.PRESCRIPTION_LOCKED);
        }
        Prescription prescription = prescriptionRepository.findWithItemsByVisitId(visitId).orElse(null);
        if (prescription != null && prescription.getStatus() != PrescriptionStatus.CANCELLED) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_EXISTS);
        }
        if (prescription == null) {
            prescription = Prescription.builder().visitId(visitId).build();
        } else {
            prescription.setStatus(PrescriptionStatus.PRESCRIBED);   // dùng lại đơn đã hủy (visit_id là UNIQUE)
        }
        prescription.setDoctorNotes(blankToNull(request.doctorNotes()));
        fillItems(prescription, request.items());
        return assembler.toResponse(prescriptionRepository.saveAndFlush(prescription), null);
    }

    @Override
    public PrescriptionResponse update(Long id, PrescriptionRequest request) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        Prescription existing = prescriptionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        VisitSummaryResponse visit = visitLookup.require(existing.getVisitId());
        assertDoctorMayPrescribe(visit, doctorId);
        PrescriptionResponse updated = lockService.runLocked(List.of(BillingLocks.visit(visit.id())),
                () -> tx.execute(s -> persistUpdate(id, request)));
        return assembler.withVisit(updated, visit);
    }

    private PrescriptionResponse persistUpdate(Long id, PrescriptionRequest request) {
        Prescription prescription = prescriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        if (prescription.getStatus() != PrescriptionStatus.PRESCRIBED) {
            throw new AppException(ErrorCode.INVALID_PRESCRIPTION_STATUS);
        }
        if (invoiceRepository.hasActiveInvoice(prescription.getVisitId())) {
            throw new AppException(ErrorCode.PRESCRIPTION_LOCKED);
        }
        prescription.setDoctorNotes(blankToNull(request.doctorNotes()));
        fillItems(prescription, request.items());
        return assembler.toResponse(prescriptionRepository.saveAndFlush(prescription), null);
    }

    /**
     * Thay toàn bộ dòng thuốc. Thuốc phải đang kinh doanh, không trùng nhau, số lượng không vượt tồn kho (EX-13, kiểm tra
     * mềm: không giữ chỗ nên vẫn phải kiểm tra lại lúc xuất kho). Đơn giá chốt ở giá bán hiện tại (BR-26).
     */
    private void fillItems(Prescription prescription, List<PrescriptionItemRequest> requests) {
        Set<Long> ids = new HashSet<>();
        for (PrescriptionItemRequest r : requests) {
            if (!ids.add(r.medicineId())) {
                throw new AppException(ErrorCode.INVALID_INPUT_DATA,
                        "Thuốc #" + r.medicineId() + " xuất hiện nhiều lần trong đơn, hãy gộp thành một dòng");
            }
        }
        Map<Long, Medicine> medicines = medicineRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Medicine::getId, Function.identity()));

        prescription.getItems().clear();
        for (PrescriptionItemRequest r : requests) {
            Medicine medicine = medicines.get(r.medicineId());
            if (medicine == null || !medicine.isActive()) {
                throw new AppException(ErrorCode.MEDICINE_NOT_FOUND,
                        "Thuốc #" + r.medicineId() + " không có trong danh mục hoặc đã ngừng kinh doanh");
            }
            if (r.quantity() > medicine.getStockQuantity()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK, "Số lượng kê vượt quá số lượng tồn kho khả dụng của thuốc ["
                        + medicine.getName() + "] (còn " + medicine.getStockQuantity() + " " + medicine.getUnit()
                        + "). Vui lòng giảm số lượng hoặc kê loại thuốc thay thế");
            }
            prescription.addItem(PrescriptionItem.builder()
                    .medicine(medicine)
                    .quantity(r.quantity())
                    .unitPrice(medicine.getSalePrice())
                    .subtotal(InvoiceCalculator.money(medicine.getSalePrice().multiply(BigDecimal.valueOf(r.quantity()))))
                    .dosage(r.dosage().trim())
                    .frequency(r.frequency().trim())
                    .durationDays(r.durationDays())
                    .instructions(blankToNull(r.instructions()))
                    .build());
        }
    }

    private static void assertDoctorMayPrescribe(VisitSummaryResponse visit, Long doctorId) {
        if (!doctorId.equals(visit.doctorId())) {
            throw new AppException(ErrorCode.VISIT_NOT_ASSIGNED_TO_YOU);
        }
        if (visit.status() != VisitStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.PRESCRIPTION_LOCKED);
        }
    }

    // ================================================================== hủy đơn

    @Override
    public PrescriptionResponse cancel(Long id) {
        Long actor = SecurityUtils.getCurrentUserId();
        RoleCode role = SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        Prescription existing = prescriptionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        boolean byDoctor = role == RoleCode.VETERINARIAN;
        if (byDoctor) {
            assertDoctorMayPrescribe(visitLookup.require(existing.getVisitId()), actor);
        }
        lockService.runLocked(List.of(BillingLocks.visit(existing.getVisitId())),
                () -> tx.execute(s -> doCancel(id, byDoctor)));
        return getById(id);
    }

    private Object doCancel(Long id, boolean byDoctor) {
        Prescription prescription = prescriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_DISPENSED);
        }
        if (prescription.getStatus() != PrescriptionStatus.PRESCRIBED) {
            throw new AppException(ErrorCode.INVALID_PRESCRIPTION_STATUS);
        }
        if (byDoctor) {
            if (invoiceRepository.hasActiveInvoice(prescription.getVisitId())) {
                throw new AppException(ErrorCode.PRESCRIPTION_LOCKED);
            }
        } else {
            removeFromInvoice(prescription);
        }
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        return null;
    }

    /** Khách xin toa mua ngoài (SM-06): gỡ các dòng thuốc khỏi hóa đơn PENDING và tính lại tổng tiền. */
    private void removeFromInvoice(Prescription prescription) {
        Invoice invoice = invoiceRepository.findByVisitIdForUpdate(prescription.getVisitId()).orElseThrow(() -> new AppException(
                ErrorCode.INVALID_PRESCRIPTION_STATUS, "Hóa đơn chưa được lập, chỉ bác sĩ phụ trách mới hủy được đơn thuốc"));
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            throw new AppException(ErrorCode.INVOICE_ALREADY_PAID);
        }
        if (invoice.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_INVOICE_STATUS);
        }
        invoice.getItems().removeIf(item -> item.getItemType() == InvoiceItemType.MEDICINE);
        invoice.setPrescriptionId(null);
        InvoiceCalculator.recalculate(invoice);
        // Chiết khấu đã nhập có thể vượt tổng mới sau khi bỏ tiền thuốc (EX-14): kéo về đúng mức trần
        BigDecimal gross = InvoiceCalculator.gross(invoice);
        if (invoice.getDiscountAmount().compareTo(gross) > 0) {
            invoice.setDiscountAmount(gross);
            InvoiceCalculator.recalculate(invoice);
        }
    }

    // ================================================================== xuất thuốc

    @Override
    public PrescriptionResponse dispense(Long id) {
        Long cashierId = SecurityUtils.getCurrentUserId();
        tx.execute(s -> doDispense(id, cashierId));
        return getById(id);
    }

    private Object doDispense(Long id, Long cashierId) {
        // Khóa dòng đơn thuốc: hai lần bấm đồng thời chỉ một lần chạy, lần sau thấy DISPENSED (EX-16)
        Prescription prescription = prescriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        if (prescription.getStatus() == PrescriptionStatus.DISPENSED) {
            throw new AppException(ErrorCode.PRESCRIPTION_ALREADY_DISPENSED);
        }
        if (prescription.getStatus() != PrescriptionStatus.PRESCRIBED) {
            throw new AppException(ErrorCode.INVALID_PRESCRIPTION_STATUS);
        }
        Invoice invoice = invoiceRepository.findByPrescriptionId(id).orElse(null);
        if (!BillingAssembler.isPaid(invoice)) {
            throw new AppException(ErrorCode.INVOICE_NOT_PAID);
        }

        // Sắp theo id thuốc để hai giao dịch xuất thuốc chồng nhau luôn khóa theo cùng thứ tự, không deadlock
        List<PrescriptionItem> items = prescription.getItems().stream()
                .sorted(Comparator.comparing((PrescriptionItem item) -> item.getMedicine().getId())).toList();
        List<Medicine> touched = new ArrayList<>();
        for (PrescriptionItem item : items) {
            Medicine medicine = item.getMedicine();
            if (medicineRepository.deductStock(medicine.getId(), item.getQuantity()) == 0) {
                entityManager.refresh(medicine);
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK, "Không thể xuất kho: Thuốc [" + medicine.getName()
                        + "] trong kho không đủ số lượng để xuất theo đơn (còn " + medicine.getStockQuantity() + ", cần "
                        + item.getQuantity() + "). Vui lòng báo Bác sĩ đổi thuốc hoặc kiểm tra lại kho dược.");
            }
            entityManager.refresh(medicine);   // tồn kho thật sau khi trừ
            inventoryRepository.save(InventoryTransaction.builder()
                    .medicineId(medicine.getId())
                    .transactionType(InventoryTransactionType.EXPORT_PRESCRIPTION)
                    .quantity(item.getQuantity())
                    .balanceAfter(medicine.getStockQuantity())
                    .referenceId(prescription.getId())
                    .createdBy(cashierId)
                    .build());
            touched.add(medicine);
        }

        prescription.setStatus(PrescriptionStatus.DISPENSED);
        prescription.setDispensedBy(cashierId);
        prescription.setDispensedAt(clock.instant());
        touched.forEach(medicine -> stockAlerts.publishIfLow(medicine, cashierId));
        // Đã PAID và đã phát thuốc: ca khám xong hẳn, clinical chuyển WAITING_PAYMENT -> COMPLETED
        eventPublisher.publishEvent(new PaymentAndDispenseCompletedEvent(UUID.randomUUID(), clock.instant(), cashierId,
                prescription.getVisitId(), invoice.getId(), prescription.getId()));
        return null;
    }

    // ================================================================== tra cứu

    @Override
    public PrescriptionResponse getById(Long id) {
        PrescriptionResponse response = readTx.execute(s -> {
            Prescription prescription = prescriptionRepository.findWithItemsById(id)
                    .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
            return assembler.toResponse(prescription, invoiceRepository.findByPrescriptionId(id).orElse(null));
        });
        return authorizeAndEnrich(response);
    }

    @Override
    public PrescriptionResponse getByVisit(Long visitId) {
        PrescriptionResponse response = readTx.execute(s -> {
            Prescription prescription = prescriptionRepository.findWithItemsByVisitId(visitId)
                    .orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND));
            return assembler.toResponse(prescription,
                    invoiceRepository.findByPrescriptionId(prescription.getId()).orElse(null));
        });
        return authorizeAndEnrich(response);
    }

    @Override
    public List<PrescriptionResponse> findByVisits(java.util.Collection<Long> visitIds) {
        if (visitIds == null || visitIds.isEmpty()) {
            return List.of();
        }
        return readTx.execute(s -> prescriptionRepository
                .findWithItemsByVisitIdInAndStatusNot(visitIds, PrescriptionStatus.CANCELLED).stream()
                .map(p -> assembler.toResponse(p, null)).toList());
    }

    /** Chủ nuôi chỉ xem được đơn (chưa hủy) của thú cưng mình; người khác trả NOT_FOUND để không lộ sự tồn tại. */
    private PrescriptionResponse authorizeAndEnrich(PrescriptionResponse response) {
        boolean owner = SecurityUtils.getCurrentRole().filter(r -> r == RoleCode.PET_OWNER).isPresent();
        if (!owner) {
            return assembler.withVisit(response, visitLookup.tryFind(response.visitId()));
        }
        VisitSummaryResponse visit = visitLookup.require(response.visitId());
        if (response.status() == PrescriptionStatus.CANCELLED
                || !SecurityUtils.getCurrentUserId().equals(visit.ownerId())) {
            throw new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND);
        }
        return assembler.withVisit(response, visit);
    }

    @Override
    public PageResponse<PrescriptionSummaryResponse> search(PrescriptionStatus status, Long visitId,
                                                            boolean awaitingDispense, int page, int size) {
        // Danh sách chờ phát: đơn cũ nhất lên đầu (phục vụ theo thứ tự thanh toán); còn lại mới nhất lên đầu
        Sort sort = awaitingDispense ? Sort.by("id").ascending() : Sort.by("id").descending();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
        Page<PrescriptionSummaryResponse> result = readTx.execute(s -> prescriptionRepository
                .findAll(PrescriptionSpecifications.filter(status, visitId, awaitingDispense), pageable)
                .map(assembler::toSummary));

        Map<Long, VisitSummaryResponse> visits = visitLookup.tryFindAll(
                result.getContent().stream().map(PrescriptionSummaryResponse::visitId).collect(Collectors.toSet()));
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), item -> assembler.withVisit(item, visits.get(item.visitId())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
