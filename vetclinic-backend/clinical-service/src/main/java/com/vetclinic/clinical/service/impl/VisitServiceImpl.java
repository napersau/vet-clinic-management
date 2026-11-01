package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AppointmentClient;
import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.DoctorStatus;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.CloseVisitRequest;
import com.vetclinic.clinical.dto.request.OrderServicesRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.request.ServiceOrderItem;
import com.vetclinic.clinical.dto.request.StartExamRequest;
import com.vetclinic.clinical.dto.response.AppointmentSummaryResponse;
import com.vetclinic.clinical.dto.response.BillableServiceResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitHistoryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.dto.response.VisitSummaryResponse;
import com.vetclinic.clinical.entity.ClinicService;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import com.vetclinic.clinical.repository.ClinicServiceRepository;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import com.vetclinic.clinical.service.DoctorStatusService;
import com.vetclinic.clinical.service.QueueSchedule;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.clinical.service.VisitService;
import com.vetclinic.clinical.service.VisitSupport;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.event.VisitCheckedInEvent;
import com.vetclinic.common.event.VisitClosedToPaymentEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.lock.NamedLockService;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VisitServiceImpl implements VisitService {

    /** Phí khám lâm sàng tổng quát, tự thêm vào đợt khám khi Bác sĩ bắt đầu khám (CLAUDE.md §5.8). */
    static final String EXAM_SERVICE_CODE = "EXAM_GEN";

    private final PetVisitRepository visitRepository;
    private final PetRepository petRepository;
    private final PetVisitStatusHistoryRepository historyRepository;
    private final VisitServiceDetailRepository detailRepository;
    private final ClinicServiceRepository serviceRepository;
    private final AuthClient authClient;
    private final AppointmentClient appointmentClient;
    private final DoctorStatusService doctorStatusService;
    private final VisitSupport support;
    private final VisitAssembler assembler;
    private final NamedLockService lockService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public VisitServiceImpl(PetVisitRepository visitRepository, PetRepository petRepository,
                            PetVisitStatusHistoryRepository historyRepository,
                            VisitServiceDetailRepository detailRepository, ClinicServiceRepository serviceRepository,
                            AuthClient authClient, AppointmentClient appointmentClient,
                            DoctorStatusService doctorStatusService, VisitSupport support, VisitAssembler assembler,
                            NamedLockService lockService, ApplicationEventPublisher eventPublisher, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.visitRepository = visitRepository;
        this.petRepository = petRepository;
        this.historyRepository = historyRepository;
        this.detailRepository = detailRepository;
        this.serviceRepository = serviceRepository;
        this.authClient = authClient;
        this.appointmentClient = appointmentClient;
        this.doctorStatusService = doctorStatusService;
        this.support = support;
        this.assembler = assembler;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private record Actor(Long id, RoleCode role) {
    }

    // ================================================================== tiếp nhận (UC-19) và hàng đợi (UC-20)

    /**
     * KHÔNG @Transactional: lời gọi OpenFeign phải nằm ngoài transaction, và khóa tên phải còn giữ SAU KHI transaction
     * commit. Thứ tự: kiểm tra lịch hẹn qua Feign -> lấy khóa theo thú cưng -> [transaction: kiểm tra + tạo] -> nhả khóa.
     * Khóa theo thú cưng chặn hai lễ tân (hoặc bấm đúp) cùng tạo hai đợt khám đang diễn ra cho một thú cưng.
     */
    @Override
    public VisitResponse checkIn(CheckInRequest request) {
        Actor actor = currentActor();
        Long petId = request.petId();
        Long appointmentId = request.appointmentId();
        String symptoms = blankToNull(request.initialSymptoms());
        Instant scheduledAt = null;

        if (appointmentId != null) {
            if (visitRepository.existsByAppointmentId(appointmentId)) {
                throw new AppException(ErrorCode.VISIT_ALREADY_ACTIVE);
            }
            ApiResponse<AppointmentSummaryResponse> response = appointmentClient.getAppointment(appointmentId);
            AppointmentSummaryResponse appointment = response == null ? null : response.getData();
            if (appointment == null) {
                throw new AppException(ErrorCode.APPOINTMENT_NOT_FOUND);
            }
            if (!"CONFIRMED".equals(appointment.status()) || !LocalDate.now(clock).equals(appointment.appointmentDate())) {
                throw new AppException(ErrorCode.APPOINTMENT_NOT_READY_FOR_CHECKIN);
            }
            if (petId != null && !petId.equals(appointment.petId())) {
                throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Thú cưng không khớp với lịch hẹn");
            }
            petId = appointment.petId();
            scheduledAt = QueueSchedule.scheduledAtIfOnTime(appointment.appointmentDate(), appointment.startTime(),
                    appointment.endTime(), clock);
            if (symptoms == null) {
                symptoms = blankToNull(appointment.reason());
            }
        }
        if (petId == null) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Phải chọn thú cưng hoặc lịch hẹn để tiếp nhận");
        }

        Long finalPetId = petId;
        String finalSymptoms = symptoms;
        Instant finalScheduledAt = scheduledAt;
        return lockService.runLocked(List.of("vetclinic:visit-pet:" + petId), () -> transactionTemplate.execute(
                tx -> createVisit(actor.id(), finalPetId, appointmentId, finalSymptoms,
                        Boolean.TRUE.equals(request.emergency()), finalScheduledAt)));
    }

    private VisitResponse createVisit(Long receptionistId, Long petId, Long appointmentId, String symptoms,
                                      boolean emergency, Instant scheduledAt) {
        Pet pet = petRepository.findWithSpeciesById(petId)
                .orElseThrow(() -> new AppException(ErrorCode.PET_NOT_FOUND));
        if (visitRepository.existsByPetIdAndStatusIn(petId, VisitStatus.ACTIVE)) {
            throw new AppException(ErrorCode.VISIT_ALREADY_ACTIVE);
        }
        if (appointmentId != null && visitRepository.existsByAppointmentId(appointmentId)) {
            throw new AppException(ErrorCode.VISIT_ALREADY_ACTIVE, "Lịch hẹn này đã được tiếp nhận trước đó");
        }

        Instant now = clock.instant();
        PetVisit visit = PetVisit.builder()
                // Mã tạm duy nhất; đổi thành mã theo id ngay bên dưới (id chỉ có sau khi INSERT)
                .visitCode("TMP-" + UUID.randomUUID())
                .pet(pet)
                .appointmentId(appointmentId)
                .receptionistId(receptionistId)
                .emergency(emergency)
                .initialSymptoms(symptoms)
                .status(VisitStatus.WAITING)
                .checkedInAt(now)
                .scheduledAt(scheduledAt)
                .build();
        visit = visitRepository.saveAndFlush(visit);
        visit.setVisitCode(String.format("VIS-%d-%05d", LocalDate.now(clock).getYear(), visit.getId()));

        support.record(visit.getId(), null, VisitStatus.WAITING, receptionistId,
                (appointmentId == null ? "Tiếp nhận khách vãng lai"
                        : "Tiếp nhận khách có lịch hẹn #" + appointmentId + (scheduledAt != null ? " (đúng giờ hẹn)" : " (đến trễ, xếp như khách vãng lai)"))
                        + (emergency ? " (CẤP CỨU)" : ""));
        // Chỉ được gửi sang RabbitMQ SAU KHI commit (DomainEventRabbitPublisher): appointment-service sẽ chuyển lịch hẹn sang CHECKED_IN
        eventPublisher.publishEvent(new VisitCheckedInEvent(UUID.randomUUID(), now, receptionistId, visit.getId(),
                visit.getVisitCode(), pet.getId(), appointmentId, emergency));
        return assembler.summary(visit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitResponse> waitingQueue() {
        return assembler.summaries(visitRepository.findQueueByStatus(VisitStatus.WAITING));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VisitResponse> search(VisitStatus status, LocalDate date, Long doctorId, Long petId,
                                              String keyword, int page, int size) {
        Specification<PetVisit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (doctorId != null) predicates.add(cb.equal(root.get("doctorId"), doctorId));
            if (petId != null) predicates.add(cb.equal(root.get("pet").get("id"), petId));
            if (date != null) {
                ZoneId zone = clock.getZone();
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkedInAt"), date.atStartOfDay(zone).toInstant()));
                predicates.add(cb.lessThan(root.get("checkedInAt"), date.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("visitCode")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<PetVisit> result = visitRepository.findAll(spec, pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), assembler::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitResponse getById(Long id) {
        Actor actor = currentActor();
        PetVisit visit = visitRepository.findWithPetById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        // Chủ nuôi chỉ xem được bệnh án của thú cưng mình; của người khác trả "không tìm thấy"
        if (actor.role() == RoleCode.PET_OWNER && !visit.getPet().getOwnerId().equals(actor.id())) {
            throw new AppException(ErrorCode.VISIT_NOT_FOUND);
        }
        return assembler.detail(visit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitHistoryResponse> getHistory(Long id) {
        if (!visitRepository.existsById(id)) {
            throw new AppException(ErrorCode.VISIT_NOT_FOUND);
        }
        return historyRepository.findByVisitIdOrderByIdAsc(id).stream()
                .map(h -> new VisitHistoryResponse(h.getId(), h.getFromStatus(), h.getToStatus(),
                        h.getChangedByUserId(), h.getNotes(), h.getCreatedAt())).toList();
    }

    // ================================================================== phân công Bác sĩ (UC-21)

    /** KHÔNG @Transactional (Feign ngoài transaction, khóa tên theo Bác sĩ phải còn giữ sau commit). */
    @Override
    public VisitResponse assignDoctor(Long id, AssignDoctorRequest request) {
        Actor actor = currentActor();
        UserSummaryResponse doctor = fetchActiveDoctor(request.doctorId());
        return lockService.runLocked(List.of("vetclinic:doctor:" + doctor.id()),
                () -> transactionTemplate.execute(tx -> applyAssignment(actor.id(), id, doctor)));
    }

    private VisitResponse applyAssignment(Long receptionistId, Long visitId, UserSummaryResponse doctor) {
        PetVisit visit = support.lock(visitId);
        support.assertStatus(visit, VisitStatus.WAITING, "Chỉ phân công được ca đang chờ khám (WAITING)");
        if (doctor.id().equals(visit.getDoctorId())) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Ca này đã được phân công cho bác sĩ này");
        }

        // FR-25: kiểm tra lại trạng thái Bác sĩ NGAY TRONG giao dịch và dưới khóa của Bác sĩ đó
        DoctorStatus status = doctorStatusService.statusOf(doctor.id());
        if (status == DoctorStatus.BUSY) {
            throw new AppException(ErrorCode.DOCTOR_NOT_AVAILABLE);
        }
        if (status == DoctorStatus.AVAILABLE_PRIORITY_CALLBACK) {
            throw new AppException(ErrorCode.DOCTOR_PRIORITY_LOCKED);
        }

        boolean reassigned = visit.getDoctorId() != null;
        visit.setDoctorId(doctor.id());
        visit.setAssignedRoom(doctor.roomNumber());
        visitRepository.save(visit);
        // Phân công không đổi trạng thái: from = to, chi tiết ở notes
        support.record(visit.getId(), VisitStatus.WAITING, VisitStatus.WAITING, receptionistId,
                (reassigned ? "Đổi sang bác sĩ " : "Phân công bác sĩ ") + doctor.fullName()
                        + (doctor.roomNumber() == null ? "" : " (phòng " + doctor.roomNumber() + ")"));
        return assembler.summary(visit);
    }

    // ================================================================== khám giai đoạn 1 (UC-22, UC-23)

    @Override
    @Transactional
    public VisitResponse startExam(Long id, StartExamRequest request) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        PetVisit visit = support.lock(id);
        support.assertAssignedTo(visit, doctorId);
        support.assertStatus(visit, VisitStatus.WAITING,
                "Chỉ có thể bắt đầu khám đối với ca đang ở trạng thái Chờ khám (WAITING)");
        // BR-12: một Bác sĩ chỉ khám một ca tại một thời điểm
        if (visitRepository.existsByDoctorIdAndStatus(doctorId, VisitStatus.IN_PROGRESS)) {
            throw new AppException(ErrorCode.DOCTOR_NOT_AVAILABLE, "Bác sĩ đang có một ca khám khác chưa kết thúc!");
        }

        visit.setWeightAtVisit(request.weightAtVisit());
        visit.setTemperature(request.temperature());
        visit.setClinicalNotes(blankToNull(request.clinicalNotes()));
        visit.setStartedAt(clock.instant());
        support.transition(visit, VisitStatus.IN_PROGRESS, doctorId,
                "Bác sĩ bắt đầu khám lâm sàng Giai đoạn 1 tại phòng " + visit.getAssignedRoom());

        serviceRepository.findByCode(EXAM_SERVICE_CODE)
                .filter(ClinicService::isActive)
                .filter(s -> !detailRepository.existsByVisitAndServiceCode(visit.getId(), s.getCode()))
                .ifPresent(s -> createDetail(visit.getId(), s, 1, "Phí khám lâm sàng tổng quát (tự động)", doctorId));
        return assembler.detail(visit);
    }

    @Override
    @Transactional
    public VisitResponse orderServices(Long id, OrderServicesRequest request) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        List<Long> serviceIds = request.services().stream().map(ServiceOrderItem::serviceId).distinct().toList();
        Map<Long, ClinicService> found = serviceRepository.findByIdInAndActiveTrue(serviceIds).stream()
                .collect(Collectors.toMap(ClinicService::getId, Function.identity()));
        if (found.size() != serviceIds.size()) {
            throw new AppException(ErrorCode.SERVICE_NOT_FOUND);
        }

        PetVisit visit = support.lock(id);
        support.assertAssignedTo(visit, doctorId);
        support.assertStatus(visit, VisitStatus.IN_PROGRESS, "Chỉ chỉ định dịch vụ khi ca khám đang diễn ra (IN_PROGRESS)");

        boolean hasParaclinical = false;
        for (ServiceOrderItem item : request.services()) {
            ClinicService service = found.get(item.serviceId());
            createDetail(visit.getId(), service, item.quantity() == null ? 1 : item.quantity(), blankToNull(item.notes()), doctorId);
            hasParaclinical |= service.getCategory().isParaclinical();
        }
        // FR-33: có chỉ định Lab / CĐHA thì giải phóng Bác sĩ (ca rời IN_PROGRESS nên trạng thái Bác sĩ tính ra AVAILABLE)
        if (hasParaclinical) {
            support.transition(visit, VisitStatus.WAITING_RESULTS, doctorId,
                    "Bác sĩ chỉ định cận lâm sàng, ca chuyển sang chờ kết quả và Bác sĩ được giải phóng");
        }
        return assembler.detail(visit);
    }

    /** Snapshot đơn giá (BR-25). Lab / CĐHA chờ ở hàng đợi (PENDING); dịch vụ làm tại phòng khám thì hoàn tất ngay. */
    private VisitServiceDetail createDetail(Long visitId, ClinicService service, int quantity, String notes, Long actorId) {
        boolean paraclinical = service.getCategory().isParaclinical();
        ServiceDetailStatus status = paraclinical ? ServiceDetailStatus.PENDING : ServiceDetailStatus.COMPLETED;
        VisitServiceDetail detail = detailRepository.saveAndFlush(VisitServiceDetail.builder()
                .visitId(visitId)
                .service(service)
                .unitPrice(service.getPrice())
                .quantity(quantity)
                .subtotal(service.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .status(status)
                .notes(notes)
                .build());
        support.recordDetail(detail.getId(), null, status, paraclinical ? "ORDERED" : "COMPLETED", actorId,
                paraclinical ? notes : "Thực hiện tại phòng khám");
        return detail;
    }

    // ================================================================== khám giai đoạn 2 (UC-30, UC-33)

    @Override
    @Transactional
    public VisitResponse callBack(Long id) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        PetVisit visit = support.lock(id);
        support.assertAssignedTo(visit, doctorId);
        support.assertStatus(visit, VisitStatus.RESULTS_READY, "Chỉ gọi vào phòng được ca đã có kết quả (RESULTS_READY)");
        if (visitRepository.existsByDoctorIdAndStatus(doctorId, VisitStatus.IN_PROGRESS)) {
            throw new AppException(ErrorCode.DOCTOR_NOT_AVAILABLE, "Bác sĩ đang có một ca khám khác chưa kết thúc!");
        }
        visit.setCallbackPostponedAt(null);
        support.transition(visit, VisitStatus.IN_PROGRESS, doctorId, "Bác sĩ mời vào phòng, bắt đầu Giai đoạn 2");
        return assembler.detail(visit);
    }

    @Override
    @Transactional
    public VisitResponse postponeCallback(Long id) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        PetVisit visit = support.lock(id);
        support.assertAssignedTo(visit, doctorId);
        support.assertStatus(visit, VisitStatus.RESULTS_READY, "Chỉ tạm hoãn được ca đã có kết quả (RESULTS_READY)");
        if (visit.getCallbackPostponedAt() != null) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION, "Ca này đã được tạm hoãn trước đó");
        }
        visit.setCallbackPostponedAt(clock.instant());
        visitRepository.save(visit);
        support.record(visit.getId(), VisitStatus.RESULTS_READY, VisitStatus.RESULTS_READY, doctorId,
                "Tạm hoãn gọi tái khám do khách vắng mặt (EX-10), giải phóng Khóa ưu tiên của Bác sĩ");
        return assembler.summary(visit);
    }

    @Override
    @Transactional
    public VisitResponse closeVisit(Long id, CloseVisitRequest request) {
        Long doctorId = SecurityUtils.getCurrentUserId();
        String diagnosis = blankToNull(request.diagnosis());
        // EX-12: chặn chốt ca khi chưa có chẩn đoán xác định
        if (diagnosis == null) {
            throw new AppException(ErrorCode.DIAGNOSIS_REQUIRED);
        }

        PetVisit visit = support.lock(id);
        support.assertAssignedTo(visit, doctorId);
        support.assertStatus(visit, VisitStatus.IN_PROGRESS, "Chỉ chốt được ca đang khám (IN_PROGRESS)");
        if (detailRepository.countByVisitAndCategoriesAndStatuses(visit.getId(), ServiceCategory.PARACLINICAL,
                ServiceDetailStatus.OPEN) > 0) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION, "Còn dịch vụ cận lâm sàng chưa hoàn tất");
        }

        visit.setDiagnosis(diagnosis);
        visit.setTreatmentPlan(blankToNull(request.treatmentPlan()));
        visit.setDoctorAdvice(blankToNull(request.doctorAdvice()));
        if (request.clinicalNotes() != null) {
            visit.setClinicalNotes(blankToNull(request.clinicalNotes()));
        }
        visit.setClosedAt(clock.instant());
        // Khóa hồ sơ chuyên môn một chiều (BR-27): từ đây VisitResponse.locked = true
        support.transition(visit, VisitStatus.WAITING_PAYMENT, doctorId,
                "Bác sĩ chốt chuyên môn, khóa hồ sơ bệnh án và chuyển sang thanh toán");
        publishClosed(visit, doctorId, false);
        return assembler.detail(visit);
    }

    @Override
    @Transactional
    public VisitResponse abandon(Long id) {
        Actor actor = currentActor();
        PetVisit visit = support.lock(id);
        if (actor.role() == RoleCode.VETERINARIAN) {
            support.assertAssignedTo(visit, actor.id());
        } else if (actor.role() != RoleCode.RECEPTIONIST) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        support.assertStatus(visit, VisitStatus.RESULTS_READY,
                "Chỉ đóng ca \"khách bỏ về\" được với ca đã có kết quả (RESULTS_READY)");

        Instant now = clock.instant();
        visit.setClosedAt(now);
        visit.setCompletedAt(now);
        visit.setCallbackPostponedAt(null);
        support.transition(visit, VisitStatus.COMPLETED, actor.id(),
                "Khách bỏ về không đọc kết quả: đóng ca, lưu kết quả vào bệnh án và ghi nợ viện phí (EX-11)");
        publishClosed(visit, actor.id(), true);
        return assembler.detail(visit);
    }

    // ================================================================== hủy đợt khám

    @Override
    @Transactional
    public VisitResponse cancel(Long id, ReasonRequest request) {
        Actor actor = currentActor();
        PetVisit visit = support.lock(id);
        switch (visit.getStatus()) {
            // Khách bỏ về khi đang chờ ở sảnh hoặc chờ kết quả: Lễ tân hủy
            case WAITING, WAITING_RESULTS -> {
                if (actor.role() != RoleCode.RECEPTIONIST) {
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }
            }
            // Khách từ chối khám / chuyển viện cấp cứu giữa chừng: Bác sĩ đang khám hủy
            case IN_PROGRESS -> {
                if (actor.role() != RoleCode.VETERINARIAN) {
                    throw new AppException(ErrorCode.ACCESS_DENIED);
                }
                support.assertAssignedTo(visit, actor.id());
            }
            default -> throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể hủy đợt khám đang ở trạng thái " + visit.getStatus());
        }

        String reason = request.reason().trim();
        // EX-08: dịch vụ cận lâm sàng còn dở dang bị hủy theo và không bao giờ tính tiền
        for (VisitServiceDetail detail : detailRepository.findByVisitIdOrderByIdAsc(visit.getId())) {
            if (ServiceDetailStatus.OPEN.contains(detail.getStatus())) {
                ServiceDetailStatus from = detail.getStatus();
                detail.setStatus(ServiceDetailStatus.CANCELLED);
                detailRepository.save(detail);
                support.recordDetail(detail.getId(), from, ServiceDetailStatus.CANCELLED, "CANCELLED", actor.id(),
                        "Hủy đợt khám: " + reason);
            }
        }
        visit.setCancelReason(reason);
        visit.setClosedAt(clock.instant());
        support.transition(visit, VisitStatus.CANCELLED, actor.id(), "Hủy đợt khám: " + reason);
        return assembler.detail(visit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitResponse> myVisits() {
        return assembler.summaries(visitRepository.findByDoctorIdAndStatusInOrderByCheckedInAtAscIdAsc(
                SecurityUtils.getCurrentUserId(),
                List.of(VisitStatus.WAITING, VisitStatus.IN_PROGRESS, VisitStatus.RESULTS_READY)));
    }

    // ================================================================== không cần người dùng đăng nhập

    @Override
    @Transactional
    public void completeAfterPayment(Long visitId, Long actorUserId) {
        // Không tìm thấy là lỗi vĩnh viễn: ném lỗi để tin nhắn sau khi hết retry được đẩy sang DLQ cho người xem xét
        PetVisit visit = support.lock(visitId);
        switch (visit.getStatus()) {
            case COMPLETED -> log.debug("Đợt khám {} đã COMPLETED, bỏ qua sự kiện trùng", visit.getVisitCode());
            case WAITING_PAYMENT -> {
                visit.setCompletedAt(clock.instant());
                support.transition(visit, VisitStatus.COMPLETED,
                        actorUserId != null ? actorUserId : visit.getReceptionistId(),
                        "Đã thanh toán và xuất thuốc xong, hoàn tất đợt khám");
            }
            default -> log.warn("Bỏ qua sự kiện thanh toán của đợt khám {} đang ở trạng thái {}",
                    visit.getVisitCode(), visit.getStatus());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BillableServiceResponse> billableServices(Long visitId) {
        if (!visitRepository.existsById(visitId)) {
            throw new AppException(ErrorCode.VISIT_NOT_FOUND);
        }
        return detailRepository.findByVisitIdAndStatusOrderByIdAsc(visitId, ServiceDetailStatus.COMPLETED).stream()
                .map(d -> new BillableServiceResponse(d.getService().getId(), d.getService().getCode(),
                        d.getService().getName(), d.getService().getCategory(), d.getQuantity(), d.getUnitPrice(),
                        d.getSubtotal())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VisitSummaryResponse summary(Long visitId) {
        return visitRepository.findWithPetById(visitId).map(VisitServiceImpl::toSummary)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitSummaryResponse> summaries(Collection<Long> visitIds) {
        if (visitIds == null || visitIds.isEmpty()) {
            return List.of();
        }
        return visitRepository.findWithPetByIdIn(visitIds).stream().map(VisitServiceImpl::toSummary).toList();
    }

    private static VisitSummaryResponse toSummary(PetVisit v) {
        return new VisitSummaryResponse(v.getId(), v.getVisitCode(), v.getStatus(), v.getPet().getId(),
                v.getPet().getName(), v.getPet().getOwnerId(), v.getDoctorId());
    }

    // ================================================================== helper

    /** Chỉ được gửi sang RabbitMQ SAU KHI commit (DomainEventRabbitPublisher): billing-pharmacy-service lập hóa đơn PENDING. */
    private void publishClosed(PetVisit visit, Long actorId, boolean abandoned) {
        eventPublisher.publishEvent(new VisitClosedToPaymentEvent(UUID.randomUUID(), clock.instant(), actorId,
                visit.getId(), visit.getVisitCode(), visit.getPet().getId(), visit.getDoctorId(), abandoned));
    }

    private UserSummaryResponse fetchActiveDoctor(Long doctorId) {
        ApiResponse<UserSummaryResponse> response = authClient.getUser(doctorId);
        UserSummaryResponse doctor = response == null ? null : response.getData();
        if (doctor == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (doctor.role() != RoleCode.VETERINARIAN || !"ACTIVE".equals(doctor.status())) {
            throw new AppException(ErrorCode.INVALID_DOCTOR);
        }
        return doctor;
    }

    private Actor currentActor() {
        return new Actor(SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
