package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.request.PrescriptionItemRequest;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import com.vetclinic.billing.repository.MedicineRepository;
import com.vetclinic.billing.repository.PrescriptionRepository;
import com.vetclinic.billing.repository.PrescriptionSpecifications;
import com.vetclinic.billing.service.BillingAssembler;
import com.vetclinic.billing.service.BillingLocks;
import com.vetclinic.billing.service.InvoiceCalculator;
import com.vetclinic.billing.service.PrescriptionService;
import com.vetclinic.billing.service.VisitLookup;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Không đánh @Transactional ở mức class: các hàm vừa gọi Feign (ngoài transaction) vừa ghi DB (trong transaction) dùng
 * TransactionTemplate để ranh giới transaction tường minh và khóa tên được nhả SAU commit (xem NamedLockService).
 * NV4b bổ sung: chặn kê / sửa / hủy đơn khi đợt khám đã có hóa đơn còn hiệu lực, lễ tân hủy đơn để gỡ khỏi hóa đơn PENDING,
 * và xuất thuốc trừ kho (dispense).
 */
@Slf4j
@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PrescriptionRepository prescriptionRepository;
    private final MedicineRepository medicineRepository;
    private final VisitLookup visitLookup;
    private final BillingAssembler assembler;
    private final NamedLockService lockService;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public PrescriptionServiceImpl(PrescriptionRepository prescriptionRepository, MedicineRepository medicineRepository,
                                   VisitLookup visitLookup, BillingAssembler assembler, NamedLockService lockService,
                                   PlatformTransactionManager transactionManager) {
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.visitLookup = visitLookup;
        this.assembler = assembler;
        this.lockService = lockService;
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
        // Chạy dưới khóa của đợt khám (cùng khóa với lập hóa đơn ở NV4b)
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
        return assembler.toResponse(prescriptionRepository.saveAndFlush(prescription));
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
        prescription.setDoctorNotes(blankToNull(request.doctorNotes()));
        fillItems(prescription, request.items());
        return assembler.toResponse(prescriptionRepository.saveAndFlush(prescription));
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
        if (!byDoctor) {
            // Lễ tân / Admin chỉ hủy để gỡ thuốc khỏi hóa đơn PENDING (NV4b); chưa có hóa đơn thì đơn thuộc quyền bác sĩ
            throw new AppException(ErrorCode.INVALID_PRESCRIPTION_STATUS,
                    "Hóa đơn chưa được lập, chỉ bác sĩ phụ trách mới hủy được đơn thuốc");
        }
        prescription.setStatus(PrescriptionStatus.CANCELLED);
        return null;
    }

    // ================================================================== tra cứu

    @Override
    public PrescriptionResponse getById(Long id) {
        PrescriptionResponse response = readTx.execute(s -> assembler.toResponse(prescriptionRepository
                .findWithItemsById(id).orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND))));
        return authorizeAndEnrich(response);
    }

    @Override
    public PrescriptionResponse getByVisit(Long visitId) {
        PrescriptionResponse response = readTx.execute(s -> assembler.toResponse(prescriptionRepository
                .findWithItemsByVisitId(visitId).orElseThrow(() -> new AppException(ErrorCode.PRESCRIPTION_NOT_FOUND))));
        return authorizeAndEnrich(response);
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
    public PageResponse<PrescriptionSummaryResponse> search(PrescriptionStatus status, Long visitId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id").descending());
        Page<PrescriptionSummaryResponse> result = readTx.execute(s -> prescriptionRepository
                .findAll(PrescriptionSpecifications.filter(status, visitId), pageable)
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
