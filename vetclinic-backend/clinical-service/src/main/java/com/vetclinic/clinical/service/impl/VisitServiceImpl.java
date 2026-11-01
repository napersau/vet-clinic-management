package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AppointmentClient;
import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.DoctorStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.response.AppointmentSummaryResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitHistoryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.clinical.service.DoctorStatusService;
import com.vetclinic.clinical.service.QueueSchedule;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.clinical.service.VisitService;
import com.vetclinic.clinical.service.VisitSupport;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.event.VisitCheckedInEvent;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class VisitServiceImpl implements VisitService {

    private final PetVisitRepository visitRepository;
    private final PetRepository petRepository;
    private final PetVisitStatusHistoryRepository historyRepository;
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
                            PetVisitStatusHistoryRepository historyRepository, AuthClient authClient,
                            AppointmentClient appointmentClient, DoctorStatusService doctorStatusService,
                            VisitSupport support, VisitAssembler assembler, NamedLockService lockService,
                            ApplicationEventPublisher eventPublisher, Clock clock,
                            PlatformTransactionManager transactionManager) {
        this.visitRepository = visitRepository;
        this.petRepository = petRepository;
        this.historyRepository = historyRepository;
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

        // NV2 bổ sung tại đây: hủy theo các chỉ định cận lâm sàng còn dở để không bao giờ bị tính tiền (EX-08)
        String reason = request.reason().trim();
        visit.setCancelReason(reason);
        visit.setClosedAt(clock.instant());
        support.transition(visit, VisitStatus.CANCELLED, actor.id(), "Hủy đợt khám: " + reason);
        return assembler.detail(visit);
    }

    // ================================================================== helper

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
