package com.vetclinic.appointment.service.impl;

import com.vetclinic.appointment.client.AuthClient;
import com.vetclinic.appointment.client.PetClient;
import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.constant.BookingChannel;
import com.vetclinic.appointment.dto.request.CancelAppointmentRequest;
import com.vetclinic.appointment.dto.request.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.request.RescheduleAppointmentRequest;
import com.vetclinic.appointment.dto.response.AppointmentHistoryResponse;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.dto.response.PetSummaryResponse;
import com.vetclinic.appointment.dto.response.UserSummaryResponse;
import com.vetclinic.appointment.entity.Appointment;
import com.vetclinic.appointment.entity.AppointmentStatusHistory;
import com.vetclinic.appointment.mapper.AppointmentMapper;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.appointment.repository.AppointmentSpecifications;
import com.vetclinic.appointment.repository.AppointmentStatusHistoryRepository;
import com.vetclinic.appointment.service.AppointmentFilter;
import com.vetclinic.appointment.service.AppointmentService;
import com.vetclinic.appointment.service.SlotLockService;
import com.vetclinic.appointment.service.SlotPolicy;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.event.AppointmentCreatedEvent;
import com.vetclinic.common.event.AppointmentStatusChangedEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;
    private final AuthClient authClient;
    private final PetClient petClient;
    private final SlotPolicy slotPolicy;
    private final SlotLockService slotLockService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final AppointmentMapper mapper;
    private final BookingProperties properties;
    private final Clock clock;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                  AppointmentStatusHistoryRepository historyRepository,
                                  AuthClient authClient,
                                  PetClient petClient,
                                  SlotPolicy slotPolicy,
                                  SlotLockService slotLockService,
                                  PlatformTransactionManager transactionManager,
                                  ApplicationEventPublisher eventPublisher,
                                  AppointmentMapper mapper,
                                  BookingProperties properties,
                                  Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.authClient = authClient;
        this.petClient = petClient;
        this.slotPolicy = slotPolicy;
        this.slotLockService = slotLockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    private record Actor(Long id, RoleCode role) {
    }

    private record Booking(Long ownerId, BookingChannel channel, AppointmentStatus status) {
    }

    // ================================================================== tạo lịch hẹn (UC-14, UC-16)

    /**
     * KHÔNG đánh dấu @Transactional: các lời gọi OpenFeign phải nằm ngoài transaction (quy chuẩn §9.1), và khóa
     * khung giờ phải còn giữ SAU KHI transaction commit. Thứ tự: kiểm tra đầu vào -> Feign -> lấy khóa
     * -> [transaction: đếm chỗ + chèn] -> nhả khóa.
     */
    @Override
    public AppointmentResponse create(CreateAppointmentRequest request) {
        Actor actor = currentActor();
        LocalDate date = request.appointmentDate();
        LocalTime start = request.startTime();
        slotPolicy.assertBookable(date, start);

        Booking booking = resolveBooking(actor, request);
        verifyPet(request.petId(), booking.ownerId());

        int capacity = slotPolicy.activeDoctorCapacity();
        if (capacity <= 0) {
            throw new AppException(ErrorCode.SLOT_CAPACITY_EXCEEDED,
                    "Hiện chưa có bác sĩ nào đang hoạt động nên chưa nhận đặt lịch");
        }

        List<String> locks = List.of(SlotLockService.slotLock(date, start),
                SlotLockService.petDayLock(request.petId(), date));
        Appointment saved = slotLockService.runLocked(locks,
                () -> transactionTemplate.execute(tx -> insertAppointment(actor, request, booking, capacity)));
        return mapper.toResponse(saved);
    }

    private Booking resolveBooking(Actor actor, CreateAppointmentRequest request) {
        return switch (actor.role()) {
            // Chủ nuôi: ownerId lấy từ token, không bao giờ tin vào body
            case PET_OWNER -> new Booking(actor.id(), BookingChannel.ONLINE, AppointmentStatus.PENDING);
            case RECEPTIONIST -> {
                if (request.ownerId() == null) {
                    throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Lễ tân phải chọn chủ thú cưng (ownerId)");
                }
                if (request.bookingChannel() == null || request.bookingChannel() == BookingChannel.ONLINE) {
                    throw new AppException(ErrorCode.INVALID_INPUT_DATA,
                            "Kênh đặt lịch của lễ tân phải là PHONE hoặc WALK_IN");
                }
                assertActivePetOwner(request.ownerId());
                yield new Booking(request.ownerId(), request.bookingChannel(), AppointmentStatus.CONFIRMED);
            }
            default -> throw new AppException(ErrorCode.ACCESS_DENIED);
        };
    }

    /** Chạy trong transaction + khóa khung giờ: đếm chỗ và chèn là một bước không bị chen ngang. */
    private Appointment insertAppointment(Actor actor, CreateAppointmentRequest request, Booking booking, int capacity) {
        LocalDate date = request.appointmentDate();
        LocalTime start = request.startTime();

        long booked = appointmentRepository.countByAppointmentDateAndStartTimeAndStatusIn(
                date, start, AppointmentStatus.OCCUPYING);
        if (booked >= capacity) {
            throw new AppException(ErrorCode.SLOT_CAPACITY_EXCEEDED);
        }
        if (appointmentRepository.existsByPetIdAndAppointmentDateAndStatusIn(
                request.petId(), date, AppointmentStatus.OCCUPYING)) {
            throw new AppException(ErrorCode.DUPLICATE_APPOINTMENT);
        }

        Instant now = clock.instant();
        boolean confirmed = booking.status() == AppointmentStatus.CONFIRMED;
        Appointment appointment = Appointment.builder()
                // Mã tạm duy nhất; đổi thành mã theo id ngay bên dưới (id chỉ có sau khi INSERT)
                .appointmentCode("TMP-" + UUID.randomUUID())
                .petId(request.petId())
                .ownerId(booking.ownerId())
                .appointmentDate(date)
                .startTime(start)
                .endTime(slotPolicy.endOf(start))
                .reason(request.reason().trim())
                .bookingChannel(booking.channel())
                .status(booking.status())
                .confirmedBy(confirmed ? actor.id() : null)
                .confirmedAt(confirmed ? now : null)
                .build();
        appointment = appointmentRepository.saveAndFlush(appointment);
        appointment.setAppointmentCode(buildCode(appointment.getId()));

        recordHistory(appointment.getId(), null, booking.status(), actor.id(),
                confirmed ? "Lễ tân tạo lịch hẹn qua kênh " + booking.channel()
                        : "Chủ nuôi gửi yêu cầu đặt lịch qua cổng trực tuyến");

        // Chỉ được gửi sang RabbitMQ SAU KHI commit (DomainEventRabbitPublisher)
        eventPublisher.publishEvent(new AppointmentCreatedEvent(UUID.randomUUID(), now, actor.id(),
                appointment.getId(), appointment.getAppointmentCode(), appointment.getOwnerId(),
                appointment.getPetId(), date, start, appointment.getStatus().name()));
        return appointment;
    }

    // ================================================================== tra cứu

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(AppointmentFilter filter) {
        Actor actor = currentActor();
        // Chủ nuôi chỉ thấy lịch hẹn của chính mình, bất kể tham số gửi lên
        Long ownerId = actor.role() == RoleCode.PET_OWNER ? actor.id() : filter.ownerId();

        Sort.Direction direction = filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = "createdAt".equalsIgnoreCase(filter.sortBy())
                ? Sort.by(direction, "createdAt", "id")
                : Sort.by(direction, "appointmentDate", "startTime", "id");
        PageRequest pageable = PageRequest.of(Math.max(filter.page(), 0),
                Math.min(Math.max(filter.size(), 1), MAX_PAGE_SIZE), sort);

        Page<Appointment> result = appointmentRepository.findAll(AppointmentSpecifications.filter(
                ownerId, filter.petId(), filter.code(), filter.fromDate(), filter.toDate(), filter.status()), pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        Actor actor = currentActor();
        Appointment appointment = findById(id);
        assertOwnerAccess(actor, appointment);
        return mapper.toResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentHistoryResponse> getHistory(Long id) {
        findById(id);
        return historyRepository.findByAppointmentIdOrderByIdAsc(id).stream()
                .map(mapper::toHistoryResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getForInternal(Long id) {
        return mapper.toResponse(findById(id));
    }

    // ================================================================== chuyển trạng thái (UC-15, UC-17)

    @Override
    @Transactional
    public AppointmentResponse confirm(Long id) {
        Actor actor = currentActor();
        requireRole(actor, RoleCode.RECEPTIONIST);

        Appointment appointment = lockForUpdate(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS,
                    "Chỉ lịch hẹn đang chờ xác nhận (PENDING) mới được duyệt");
        }
        appointment.setConfirmedBy(actor.id());
        appointment.setConfirmedAt(clock.instant());
        transition(appointment, AppointmentStatus.CONFIRMED, actor.id(), "Lễ tân xác nhận lịch hẹn");
        return mapper.toResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long id, CancelAppointmentRequest request) {
        Actor actor = currentActor();
        if (actor.role() != RoleCode.PET_OWNER && actor.role() != RoleCode.RECEPTIONIST) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Appointment appointment = lockForUpdate(id);
        assertOwnerAccess(actor, appointment);
        if (!AppointmentStatus.MODIFIABLE.contains(appointment.getStatus())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS,
                    "Chỉ lịch hẹn đang chờ xác nhận hoặc đã xác nhận mới hủy được");
        }
        boolean byOwner = actor.role() == RoleCode.PET_OWNER;
        if (byOwner) {
            assertOwnerTimeWindow(appointment, "hủy");
        }

        String reason = request.reason().trim();
        appointment.setCancelReason(reason);
        appointment.setCancelledBy(actor.id());
        appointment.setCancelledAt(clock.instant());
        transition(appointment, AppointmentStatus.CANCELLED, actor.id(),
                (byOwner ? "Chủ nuôi tự hủy lịch hẹn: " : "Lễ tân hủy / từ chối lịch hẹn: ") + reason);
        return mapper.toResponse(appointment);
    }

    /**
     * Không đánh dấu @Transactional vì lý do giống create (Feign ngoài transaction, khóa khung giờ mới).
     */
    @Override
    public AppointmentResponse reschedule(Long id, RescheduleAppointmentRequest request) {
        Actor actor = currentActor();
        if (actor.role() != RoleCode.PET_OWNER && actor.role() != RoleCode.RECEPTIONIST) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        LocalDate newDate = request.newDate();
        LocalTime newStart = request.newStartTime();

        // Đọc nhanh (không khóa) để biết thú cưng và kiểm tra quyền; điều kiện thật được kiểm tra lại trong transaction
        Appointment current = findById(id);
        assertOwnerAccess(actor, current);
        slotPolicy.assertBookable(newDate, newStart);

        int capacity = slotPolicy.activeDoctorCapacity();
        if (capacity <= 0) {
            throw new AppException(ErrorCode.SLOT_CAPACITY_EXCEEDED,
                    "Hiện chưa có bác sĩ nào đang hoạt động nên chưa nhận đặt lịch");
        }

        List<String> locks = List.of(SlotLockService.slotLock(newDate, newStart),
                SlotLockService.petDayLock(current.getPetId(), newDate));
        Appointment saved = slotLockService.runLocked(locks,
                () -> transactionTemplate.execute(tx -> applyReschedule(actor, id, request, capacity)));
        return mapper.toResponse(saved);
    }

    private Appointment applyReschedule(Actor actor, Long id, RescheduleAppointmentRequest request, int capacity) {
        LocalDate newDate = request.newDate();
        LocalTime newStart = request.newStartTime();

        Appointment appointment = lockForUpdate(id);
        assertOwnerAccess(actor, appointment);
        if (!AppointmentStatus.MODIFIABLE.contains(appointment.getStatus())) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS,
                    "Chỉ lịch hẹn đang chờ xác nhận hoặc đã xác nhận mới đổi được");
        }
        if (actor.role() == RoleCode.PET_OWNER) {
            assertOwnerTimeWindow(appointment, "đổi");
        }
        if (newDate.equals(appointment.getAppointmentDate()) && newStart.equals(appointment.getStartTime())) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Khung giờ mới trùng với khung giờ hiện tại");
        }

        long booked = appointmentRepository.countByAppointmentDateAndStartTimeAndStatusIn(
                newDate, newStart, AppointmentStatus.OCCUPYING);
        if (booked >= capacity) {
            throw new AppException(ErrorCode.SLOT_CAPACITY_EXCEEDED);
        }
        if (appointmentRepository.existsByPetIdAndAppointmentDateAndStatusInAndIdNot(
                appointment.getPetId(), newDate, AppointmentStatus.OCCUPYING, appointment.getId())) {
            throw new AppException(ErrorCode.DUPLICATE_APPOINTMENT);
        }

        String note = "Đổi lịch từ " + appointment.getAppointmentDate() + " " + appointment.getStartTime()
                + " sang " + newDate + " " + newStart
                + (request.reason() == null || request.reason().isBlank() ? "" : ". Lý do: " + request.reason().trim());
        appointment.setAppointmentDate(newDate);
        appointment.setStartTime(newStart);
        appointment.setEndTime(slotPolicy.endOf(newStart));
        appointmentRepository.save(appointment);
        // Đổi lịch không đổi trạng thái: from = to, chi tiết ở notes
        recordHistory(appointment.getId(), appointment.getStatus(), appointment.getStatus(), actor.id(), note);
        // Lễ tân dời giờ hộ thì chủ nuôi cần được báo; chủ nuôi tự đổi thì không
        if (actor.role() != RoleCode.PET_OWNER) {
            notifyOwner(appointment, actor.id(), true, request.reason() == null ? null : request.reason().trim());
        }
        return appointment;
    }

    // ================================================================== vắng mặt (EX-01) và check-in

    @Override
    @Transactional
    public AppointmentResponse markNoShow(Long id) {
        Actor actor = currentActor();
        requireRole(actor, RoleCode.RECEPTIONIST);

        Appointment appointment = lockForUpdate(id);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS,
                    "Chỉ lịch hẹn đã xác nhận (CONFIRMED) mới đánh dấu vắng mặt được");
        }
        if (!isNoShowDue(appointment)) {
            throw new AppException(ErrorCode.INVALID_APPOINTMENT_STATUS,
                    "Chưa quá " + properties.noShowGraceMinutes() + " phút sau giờ kết thúc khung hẹn nên chưa thể đánh dấu vắng mặt");
        }
        transition(appointment, AppointmentStatus.NO_SHOW, actor.id(), "Lễ tân xác nhận khách vắng mặt (No-show)");
        return mapper.toResponse(appointment);
    }

    @Override
    @Transactional
    public boolean markNoShowBySystem(Long id) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id).orElse(null);
        // Idempotent: khách vừa check-in hoặc lịch vừa bị hủy trong lúc tiến trình quét đang chạy thì bỏ qua
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CONFIRMED || !isNoShowDue(appointment)) {
            return false;
        }
        transition(appointment, AppointmentStatus.NO_SHOW, null,
                "Hệ thống tự động chuyển NO_SHOW do quá " + properties.noShowGraceMinutes()
                        + " phút sau giờ hẹn mà khách chưa làm thủ tục tiếp đón.");
        return true;
    }

    @Override
    @Transactional
    public boolean expirePendingBySystem(Long id) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id).orElse(null);
        // Idempotent: lễ tân vừa duyệt hoặc chủ nuôi vừa hủy trong lúc tiến trình quét chạy thì bỏ qua
        if (appointment == null || appointment.getStatus() != AppointmentStatus.PENDING
                || !slotPolicy.now().isAfter(LocalDateTime.of(appointment.getAppointmentDate(), appointment.getEndTime()))) {
            return false;
        }
        String reason = "Quá giờ hẹn mà phòng khám chưa xác nhận";
        appointment.setCancelReason(reason);
        appointment.setCancelledAt(clock.instant());   // cancelledBy để trống = hệ thống
        transition(appointment, AppointmentStatus.CANCELLED, null, "Hệ thống tự động hủy: " + reason.toLowerCase(java.util.Locale.ROOT) + ".");
        return true;
    }

    @Override
    @Transactional
    public void markCheckedIn(Long appointmentId, Long actorUserId, String visitCode) {
        // Không tìm thấy là lỗi vĩnh viễn: ném lỗi để message sau khi hết retry được đẩy sang DLQ cho người xem xét
        Appointment appointment = lockForUpdate(appointmentId);
        switch (appointment.getStatus()) {
            case CHECKED_IN -> log.debug("Lịch hẹn {} đã CHECKED_IN, bỏ qua sự kiện trùng", appointmentId);
            case CONFIRMED -> transition(appointment, AppointmentStatus.CHECKED_IN, actorUserId,
                    "Khách đã tiếp nhận tại quầy, đợt khám " + visitCode);
            default -> log.warn("Bỏ qua sự kiện check-in của lịch hẹn {} đang ở trạng thái {} (đợt khám {})",
                    appointmentId, appointment.getStatus(), visitCode);
        }
    }

    // ================================================================== helper

    private boolean isNoShowDue(Appointment appointment) {
        LocalDateTime deadline = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getEndTime())
                .plusMinutes(properties.noShowGraceMinutes());
        return slotPolicy.now().isAfter(deadline);
    }

    /** Chủ nuôi chỉ tự hủy / đổi được trước giờ hẹn tối thiểu N giờ; lễ tân không bị giới hạn này. */
    private void assertOwnerTimeWindow(Appointment appointment, String verb) {
        LocalDateTime start = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getStartTime());
        if (slotPolicy.now().isAfter(start.minusHours(properties.minCancelHoursBefore()))) {
            throw new AppException(ErrorCode.CANNOT_CANCEL_APPOINTMENT,
                    "Đã quá thời gian cho phép tự " + verb + " lịch trên hệ thống (tối thiểu "
                            + properties.minCancelHoursBefore() + " tiếng trước giờ hẹn). "
                            + "Vui lòng liên hệ trực tiếp hotline phòng khám để được hỗ trợ");
        }
    }

    private void transition(Appointment appointment, AppointmentStatus to, Long actorId, String note) {
        AppointmentStatus from = appointment.getStatus();
        appointment.setStatus(to);
        appointmentRepository.save(appointment);
        recordHistory(appointment.getId(), from, to, actorId, note);
        // CHECKED_IN đã có thông báo riêng từ clinical-service (visit.checked_in); hành động của chính chủ nuôi không cần báo lại
        boolean ownerNotifiable = to == AppointmentStatus.CONFIRMED || to == AppointmentStatus.CANCELLED || to == AppointmentStatus.NO_SHOW;
        if (ownerNotifiable && !appointment.getOwnerId().equals(actorId)) {
            notifyOwner(appointment, actorId, false, appointment.getCancelReason());
        }
    }

    /** Phát sự kiện báo chủ nuôi; chỉ được gửi sang RabbitMQ SAU KHI transaction commit (DomainEventRabbitPublisher). */
    private void notifyOwner(Appointment appointment, Long actorId, boolean rescheduled, String reason) {
        eventPublisher.publishEvent(new AppointmentStatusChangedEvent(UUID.randomUUID(), clock.instant(), actorId,
                appointment.getId(), appointment.getAppointmentCode(), appointment.getOwnerId(), appointment.getPetId(),
                appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getStatus().name(), rescheduled, reason));
    }

    private void recordHistory(Long appointmentId, AppointmentStatus from, AppointmentStatus to,
                               Long actorId, String note) {
        historyRepository.save(AppointmentStatusHistory.builder()
                .appointmentId(appointmentId)
                .fromStatus(from)
                .toStatus(to)
                .changedByUserId(actorId)
                .notes(note)
                .build());
    }

    private Appointment findById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    private Appointment lockForUpdate(Long id) {
        return appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.APPOINTMENT_NOT_FOUND));
    }

    /** Chủ nuôi chỉ thao tác được trên lịch hẹn của mình; của người khác trả "không tìm thấy" để không lộ sự tồn tại. */
    private void assertOwnerAccess(Actor actor, Appointment appointment) {
        if (actor.role() == RoleCode.PET_OWNER && !appointment.getOwnerId().equals(actor.id())) {
            throw new AppException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
    }

    private void requireRole(Actor actor, RoleCode required) {
        if (actor.role() != required) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Actor currentActor() {
        return new Actor(SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)));
    }

    /** Lễ tân đặt hộ phải chọn đúng một tài khoản chủ thú cưng còn hoạt động. */
    private void assertActivePetOwner(Long ownerId) {
        ApiResponse<UserSummaryResponse> response = authClient.getUser(ownerId);
        UserSummaryResponse owner = response == null ? null : response.getData();
        if (owner == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (owner.role() != RoleCode.PET_OWNER) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Người dùng được chọn không phải chủ thú cưng");
        }
        if (!"ACTIVE".equals(owner.status())) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Tài khoản chủ thú cưng không còn hoạt động");
        }
    }

    /** Thú cưng phải tồn tại và thuộc đúng chủ nuôi. Bật bằng vetclinic.booking.verify-pet. */
    private void verifyPet(Long petId, Long ownerId) {
        if (!properties.verifyPet()) {
            return;
        }
        ApiResponse<PetSummaryResponse> response = petClient.getPet(petId);
        PetSummaryResponse pet = response == null ? null : response.getData();
        // Thú cưng của người khác cũng trả PET_NOT_FOUND để không lộ sự tồn tại của hồ sơ
        if (pet == null || !ownerId.equals(pet.ownerId())) {
            throw new AppException(ErrorCode.PET_NOT_FOUND);
        }
    }

    /** APT-yyyyMMdd-NNNNN: ngày tạo + id nên luôn duy nhất, không cần sinh số rồi thử lại khi trùng. */
    private String buildCode(Long id) {
        return "APT-" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%05d", id);
    }
}
