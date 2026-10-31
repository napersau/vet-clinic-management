package com.vetclinic.appointment.service.impl;

import com.vetclinic.appointment.client.AuthClient;
import com.vetclinic.appointment.client.PetClient;
import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.constant.BookingChannel;
import com.vetclinic.appointment.dto.request.CancelAppointmentRequest;
import com.vetclinic.appointment.dto.request.CreateAppointmentRequest;
import com.vetclinic.appointment.dto.request.RescheduleAppointmentRequest;
import com.vetclinic.appointment.dto.response.AppointmentResponse;
import com.vetclinic.appointment.dto.response.PetSummaryResponse;
import com.vetclinic.appointment.dto.response.UserSummaryResponse;
import com.vetclinic.appointment.entity.Appointment;
import com.vetclinic.appointment.entity.AppointmentStatusHistory;
import com.vetclinic.appointment.mapper.AppointmentMapperImpl;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.appointment.repository.AppointmentStatusHistoryRepository;
import com.vetclinic.appointment.service.SlotLockService;
import com.vetclinic.appointment.service.SlotPolicy;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.event.AppointmentCreatedEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    private static final List<String> STANDARD_SLOTS = List.of("08:00", "08:30", "09:00", "09:30", "10:00", "10:30",
            "11:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30");

    // 2026-10-30 09:00 giờ Việt Nam
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final long OWNER = 5L;
    private static final long OTHER_OWNER = 6L;
    private static final long RECEPTIONIST = 9L;
    private static final long PET = 11L;

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentStatusHistoryRepository historyRepository;
    @Mock
    private AuthClient authClient;
    @Mock
    private PetClient petClient;
    @Mock
    private SlotLockService slotLockService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = newService(false);
        // Khóa khung giờ giả: chạy thẳng action, giống khi lấy được khóa
        lenient().when(slotLockService.runLocked(anyCollection(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private AppointmentServiceImpl newService(boolean verifyPet) {
        BookingProperties properties = new BookingProperties("Asia/Ho_Chi_Minh", 30,
                STANDARD_SLOTS, 30, 2, 60, verifyPet, 5);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new AppointmentServiceImpl(appointmentRepository, historyRepository, authClient, petClient,
                new SlotPolicy(properties, CLOCK, authClient), slotLockService, transactionManager, eventPublisher,
                new AppointmentMapperImpl(), properties, CLOCK);
    }

    // ================================================================== tạo lịch hẹn

    @Test
    void create_byOwner_savesPendingOnlineAppointmentAndPublishesEvent() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubCapacity(2);
        stubSave();
        when(appointmentRepository.countByAppointmentDateAndStartTimeAndStatusIn(
                TOMORROW, LocalTime.of(9, 0), AppointmentStatus.OCCUPYING)).thenReturn(1L);

        // Body cố tình gửi ownerId và kênh khác: phải bị bỏ qua, chủ nuôi chỉ đặt cho chính mình qua ONLINE
        AppointmentResponse response = service.create(new CreateAppointmentRequest(
                PET, OTHER_OWNER, TOMORROW, LocalTime.of(9, 0), "  Chó bỏ ăn 2 ngày  ", BookingChannel.PHONE));

        assertThat(response.status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.bookingChannel()).isEqualTo(BookingChannel.ONLINE);
        assertThat(response.ownerId()).isEqualTo(OWNER);
        assertThat(response.reason()).isEqualTo("Chó bỏ ăn 2 ngày");
        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.appointmentCode()).isEqualTo("APT-20261030-00100");
        assertThat(response.confirmedBy()).isNull();

        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(history.getChangedByUserId()).isEqualTo(OWNER);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        AppointmentCreatedEvent created = (AppointmentCreatedEvent) event.getValue();
        assertThat(created.appointmentId()).isEqualTo(100L);
        assertThat(created.ownerId()).isEqualTo(OWNER);
        assertThat(created.status()).isEqualTo("PENDING");
    }

    @Test
    void create_takesBothSlotAndPetDayLocks() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubCapacity(2);
        stubSave();

        service.create(new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> locks = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(slotLockService).runLocked(locks.capture(), any());
        assertThat(locks.getValue()).containsExactlyInAnyOrder(
                SlotLockService.slotLock(TOMORROW, LocalTime.of(9, 0)),
                SlotLockService.petDayLock(PET, TOMORROW));
    }

    @Test
    void create_whenSlotIsFull_throwsCapacityExceededAndSavesNothing() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubCapacity(2);
        when(appointmentRepository.countByAppointmentDateAndStartTimeAndStatusIn(
                TOMORROW, LocalTime.of(9, 0), AppointmentStatus.OCCUPYING)).thenReturn(2L);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SLOT_CAPACITY_EXCEEDED);

        verify(appointmentRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void create_withNoActiveDoctors_isRefusedWithoutTakingAnyLock() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubCapacity(0);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SLOT_CAPACITY_EXCEEDED);

        verify(slotLockService, never()).runLocked(anyCollection(), any());
    }

    @Test
    void create_samePetSameDay_throwsDuplicate() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubCapacity(2);
        when(appointmentRepository.existsByPetIdAndAppointmentDateAndStatusIn(
                PET, TOMORROW, AppointmentStatus.OCCUPYING)).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(10, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_APPOINTMENT);
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_byReceptionist_isConfirmedImmediatelyWithConfirmer() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(authClient.getUser(77L)).thenReturn(ApiResponse.success(
                new UserSummaryResponse(77L, "Chủ Nuôi", "0944444444", RoleCode.PET_OWNER, "ACTIVE")));
        stubCapacity(1);
        stubSave();

        AppointmentResponse response = service.create(new CreateAppointmentRequest(
                PET, 77L, TOMORROW, LocalTime.of(14, 0), "Khám tổng quát", BookingChannel.PHONE));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.bookingChannel()).isEqualTo(BookingChannel.PHONE);
        assertThat(response.ownerId()).isEqualTo(77L);
        assertThat(response.confirmedBy()).isEqualTo(RECEPTIONIST);
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(lastHistory().getToStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void create_byReceptionist_requiresOwnerAndPhoneOrWalkInChannel() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);

        assertThatThrownBy(() -> service.create(new CreateAppointmentRequest(
                PET, null, TOMORROW, LocalTime.of(14, 0), "Khám", BookingChannel.PHONE)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
        assertThatThrownBy(() -> service.create(new CreateAppointmentRequest(
                PET, 77L, TOMORROW, LocalTime.of(14, 0), "Khám", BookingChannel.ONLINE)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
        assertThatThrownBy(() -> service.create(new CreateAppointmentRequest(
                PET, 77L, TOMORROW, LocalTime.of(14, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
    }

    @Test
    void create_byReceptionist_forUserThatIsNotAnActivePetOwner_isRejected() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(authClient.getUser(3L)).thenReturn(ApiResponse.success(
                new UserSummaryResponse(3L, "Bác sĩ", "0911111111", RoleCode.VETERINARIAN, "ACTIVE")));
        when(authClient.getUser(4L)).thenReturn(ApiResponse.success(
                new UserSummaryResponse(4L, "Chủ cũ", "0922222222", RoleCode.PET_OWNER, "LOCKED")));

        for (long ownerId : new long[]{3L, 4L}) {
            assertThatThrownBy(() -> service.create(new CreateAppointmentRequest(
                    PET, ownerId, TOMORROW, LocalTime.of(14, 0), "Khám", BookingChannel.WALK_IN)))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
        }
    }

    @Test
    void create_byOtherRoles_isForbidden() {
        loginAs(3L, RoleCode.VETERINARIAN);

        assertThatThrownBy(() -> service.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void create_rejectsInvalidSlotPastTimeAndDatesOutsideHorizon() {
        loginAs(OWNER, RoleCode.PET_OWNER);

        // Không phải khung chuẩn
        assertCreateFails(TOMORROW, LocalTime.of(9, 15), ErrorCode.INVALID_APPOINTMENT_SLOT);
        // Giờ nghỉ trưa
        assertCreateFails(TOMORROW, LocalTime.of(12, 30), ErrorCode.INVALID_APPOINTMENT_SLOT);
        // Hôm qua
        assertCreateFails(TODAY.minusDays(1), LocalTime.of(9, 0), ErrorCode.INVALID_INPUT_DATA);
        // Hôm nay nhưng khung đã bắt đầu (bây giờ 09:00)
        assertCreateFails(TODAY, LocalTime.of(9, 0), ErrorCode.INVALID_INPUT_DATA);
        assertCreateFails(TODAY, LocalTime.of(8, 30), ErrorCode.INVALID_INPUT_DATA);
        // Quá 60 ngày
        assertCreateFails(TODAY.plusDays(61), LocalTime.of(9, 0), ErrorCode.INVALID_INPUT_DATA);
    }

    @Test
    void create_withVerifyPetOn_rejectsPetBelongingToSomeoneElse() {
        AppointmentServiceImpl strict = newService(true);
        loginAs(OWNER, RoleCode.PET_OWNER);
        when(petClient.getPet(PET)).thenReturn(ApiResponse.success(new PetSummaryResponse(PET, OTHER_OWNER, "Lu")));

        assertThatThrownBy(() -> strict.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.PET_NOT_FOUND);
        verify(slotLockService, never()).runLocked(anyCollection(), any());
    }

    @Test
    void create_withVerifyPetOn_acceptsOwnPet() {
        AppointmentServiceImpl strict = newService(true);
        loginAs(OWNER, RoleCode.PET_OWNER);
        when(petClient.getPet(PET)).thenReturn(ApiResponse.success(new PetSummaryResponse(PET, OWNER, "Lu")));
        stubCapacity(2);
        stubSave();

        AppointmentResponse response = strict.create(
                new CreateAppointmentRequest(PET, null, TOMORROW, LocalTime.of(9, 0), "Khám", null));

        assertThat(response.petId()).isEqualTo(PET);
    }

    // ================================================================== duyệt, hủy

    @Test
    void confirm_movesPendingToConfirmedAndRecordsReceptionist() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        AppointmentResponse response = service.confirm(1L);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(a.getConfirmedBy()).isEqualTo(RECEPTIONIST);
        assertThat(a.getConfirmedAt()).isNotNull();
        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirm_isNotRepeatableAndOnlyForReceptionist() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_APPOINTMENT_STATUS);
        verify(historyRepository, never()).save(any());

        loginAs(OWNER, RoleCode.PET_OWNER);
        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void cancel_byOwnerWellBeforeAppointment_isAllowed() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TOMORROW, LocalTime.of(10, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        AppointmentResponse response = service.cancel(1L, new CancelAppointmentRequest("  Bận việc đột xuất "));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancelReason()).isEqualTo("Bận việc đột xuất");
        assertThat(a.getCancelledBy()).isEqualTo(OWNER);
        assertThat(a.getCancelledAt()).isNotNull();
        assertThat(lastHistory().getNotes()).contains("Chủ nuôi tự hủy").contains("Bận việc đột xuất");
    }

    @Test
    void cancel_byOwnerLessThanTwoHoursBefore_isRefused() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        // Bây giờ 09:00, hẹn 10:30 -> còn 1,5 giờ
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY, LocalTime.of(10, 30));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(1L, new CancelAppointmentRequest("Bận")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CANNOT_CANCEL_APPOINTMENT);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void cancel_byOwnerExactlyTwoHoursBefore_isStillAllowed() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TODAY, LocalTime.of(11, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.cancel(1L, new CancelAppointmentRequest("Bận")).status())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_ofSomeoneElsesAppointment_looksLikeNotFound() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OTHER_OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(10, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(1L, new CancelAppointmentRequest("Bận")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND);
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    private com.vetclinic.common.event.AppointmentStatusChangedEvent publishedStatusEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (com.vetclinic.common.event.AppointmentStatusChangedEvent) captor.getValue();
    }

    @Test
    void confirm_notifiesTheOwner_UC17() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        service.confirm(1L);

        var event = publishedStatusEvent();
        assertThat(event.ownerId()).isEqualTo(OWNER);
        assertThat(event.status()).isEqualTo("CONFIRMED");
        assertThat(event.rescheduled()).isFalse();
        assertThat(event.actorUserId()).isEqualTo(RECEPTIONIST);
    }

    @Test
    void cancel_byReceptionistCarriesTheReasonToTheOwner_butOwnersOwnCancelDoesNot() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        service.cancel(1L, new CancelAppointmentRequest("Phòng khám bảo trì thiết bị"));

        var event = publishedStatusEvent();
        assertThat(event.status()).isEqualTo("CANCELLED");
        assertThat(event.reason()).isEqualTo("Phòng khám bảo trì thiết bị");

        org.mockito.Mockito.clearInvocations(eventPublisher);
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment own = appointment(2L, OWNER, AppointmentStatus.CONFIRMED, TOMORROW, LocalTime.of(10, 0));
        when(appointmentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(own));
        service.cancel(2L, new CancelAppointmentRequest("Bận việc đột xuất"));

        verify(eventPublisher, never()).publishEvent(any(Object.class));   // chủ nuôi tự hủy: đã thấy kết quả trên màn hình
    }

    @Test
    void systemNoShow_notifiesTheOwnerWithNoActor() {
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, LocalDate.of(2026, 10, 29), LocalTime.of(8, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.markNoShowBySystem(1L)).isTrue();

        var event = publishedStatusEvent();
        assertThat(event.status()).isEqualTo("NO_SHOW");
        assertThat(event.actorUserId()).isNull();
    }

    @Test
    void cancel_byReceptionist_ignoresTheTwoHourRule() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TODAY, LocalTime.of(9, 30));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        AppointmentResponse response = service.cancel(1L, new CancelAppointmentRequest("Phòng khám bảo trì thiết bị"));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(lastHistory().getNotes()).contains("Lễ tân hủy / từ chối");
    }

    @Test
    void cancel_ofFinishedAppointment_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        for (AppointmentStatus status : List.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            Appointment a = appointment(1L, OWNER, status, TOMORROW, LocalTime.of(10, 0));
            when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> service.cancel(1L, new CancelAppointmentRequest("Lý do")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_APPOINTMENT_STATUS);
        }
    }

    // ================================================================== đổi lịch

    @Test
    void reschedule_byOwner_movesToFreeSlotKeepingStatusAndWritesAuditNote() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        stubCapacity(2);

        AppointmentResponse response = service.reschedule(1L, new RescheduleAppointmentRequest(
                TOMORROW.plusDays(1), LocalTime.of(14, 30), "Đổi lịch công việc"));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.appointmentDate()).isEqualTo(TOMORROW.plusDays(1));
        assertThat(response.startTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(15, 0));
        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.getToStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.getNotes()).contains("2026-10-31 09:00").contains("2026-11-01 14:30").contains("Đổi lịch công việc");
    }

    @Test
    void reschedule_toFullSlot_isRefusedAndAppointmentUnchanged() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        stubCapacity(2);
        when(appointmentRepository.countByAppointmentDateAndStartTimeAndStatusIn(
                TOMORROW, LocalTime.of(10, 0), AppointmentStatus.OCCUPYING)).thenReturn(2L);

        assertThatThrownBy(() -> service.reschedule(1L, new RescheduleAppointmentRequest(TOMORROW, LocalTime.of(10, 0), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SLOT_CAPACITY_EXCEEDED);
        assertThat(a.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void reschedule_toTheSameSlot_isRefused() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        stubCapacity(2);

        assertThatThrownBy(() -> service.reschedule(1L, new RescheduleAppointmentRequest(TOMORROW, LocalTime.of(9, 0), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
    }

    @Test
    void reschedule_byOwnerLessThanTwoHoursBefore_isRefused() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY, LocalTime.of(10, 30));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));
        stubCapacity(2);

        assertThatThrownBy(() -> service.reschedule(1L, new RescheduleAppointmentRequest(TOMORROW, LocalTime.of(9, 0), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.CANNOT_CANCEL_APPOINTMENT);
    }

    @Test
    void reschedule_ofSomeoneElsesAppointment_looksLikeNotFound() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(
                appointment(1L, OTHER_OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0))));

        assertThatThrownBy(() -> service.reschedule(1L, new RescheduleAppointmentRequest(TOMORROW, LocalTime.of(10, 0), null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND);
        verify(slotLockService, never()).runLocked(anyCollection(), any());
    }

    // ================================================================== vắng mặt (EX-01)

    @Test
    void markNoShowBySystem_afterGracePeriod_movesConfirmedToNoShowWithSystemActor() {
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY.minusDays(1), LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.markNoShowBySystem(1L)).isTrue();

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(history.getToStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(history.getChangedByUserId()).isNull();   // null = hệ thống
    }

    @Test
    void markNoShowBySystem_beforeGracePeriodEnds_doesNothing() {
        // Khung 08:30-09:00, bây giờ 09:00: còn 30 phút nữa mới quá hạn
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY, LocalTime.of(8, 30));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.markNoShowBySystem(1L)).isFalse();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void markNoShowBySystem_neverTouchesAppointmentsThatAreNoLongerConfirmed() {
        // Khách vừa check-in đúng lúc tiến trình quét chạy: không được đè sang NO_SHOW
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CHECKED_IN, TODAY.minusDays(1), LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.markNoShowBySystem(1L)).isFalse();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        verify(appointmentRepository, never()).save(any());
    }

    // ================================================================== PENDING quá hạn

    @Test
    void expirePendingBySystem_afterTheSlotEnded_cancelsAndFreesTheSlotWithSystemActor() {
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TODAY, LocalTime.of(8, 0));   // khung 08:00-08:30, bây giờ 09:00
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.expirePendingBySystem(1L)).isTrue();

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.getCancelReason()).contains("chưa xác nhận");
        assertThat(a.getCancelledBy()).isNull();   // null = hệ thống
        assertThat(a.getCancelledAt()).isNotNull();
        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(history.getChangedByUserId()).isNull();
    }

    @Test
    void expirePendingBySystem_doesNothingWhileTheSlotIsStillOpen() {
        Appointment a = appointment(1L, OWNER, AppointmentStatus.PENDING, TODAY, LocalTime.of(9, 0));   // khung 09:00-09:30
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        assertThat(service.expirePendingBySystem(1L)).isFalse();
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void expirePendingBySystem_neverTouchesAppointmentsApprovedMeanwhile() {
        // Lễ tân vừa duyệt đúng lúc tiến trình quét chạy: không được hủy lịch đã CONFIRMED
        Appointment confirmed = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY.minusDays(1), LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(confirmed));
        when(appointmentRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

        assertThat(service.expirePendingBySystem(1L)).isFalse();
        assertThat(service.expirePendingBySystem(2L)).isFalse();
        assertThat(confirmed.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void markNoShow_byReceptionist_requiresGracePeriodToHavePassed() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        Appointment late = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY.minusDays(1), LocalTime.of(9, 0));
        Appointment early = appointment(2L, OWNER, AppointmentStatus.CONFIRMED, TODAY, LocalTime.of(8, 30));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(late));
        when(appointmentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(early));

        assertThat(service.markNoShow(1L).status()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(lastHistory().getChangedByUserId()).isEqualTo(RECEPTIONIST);

        assertThatThrownBy(() -> service.markNoShow(2L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_APPOINTMENT_STATUS);
        assertThat(early.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    // ================================================================== check-in (từ sự kiện của clinical-service)

    @Test
    void markCheckedIn_movesConfirmedToCheckedInWithReceptionistAsActor() {
        Appointment a = appointment(1L, OWNER, AppointmentStatus.CONFIRMED, TODAY, LocalTime.of(9, 0));
        when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

        service.markCheckedIn(1L, RECEPTIONIST, "VIS-2026-0001");

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
        AppointmentStatusHistory history = lastHistory();
        assertThat(history.getChangedByUserId()).isEqualTo(RECEPTIONIST);
        assertThat(history.getNotes()).contains("VIS-2026-0001");
    }

    @Test
    void markCheckedIn_isIdempotentAndIgnoresInconsistentStates() {
        for (AppointmentStatus status : List.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.NO_SHOW,
                AppointmentStatus.CANCELLED, AppointmentStatus.PENDING)) {
            Appointment a = appointment(1L, OWNER, status, TODAY, LocalTime.of(9, 0));
            when(appointmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(a));

            service.markCheckedIn(1L, RECEPTIONIST, "VIS-2026-0001");

            assertThat(a.getStatus()).isEqualTo(status);
        }
        verify(historyRepository, never()).save(any());
    }

    @Test
    void markCheckedIn_forUnknownAppointment_failsSoTheMessageGoesToTheDeadLetterQueue() {
        when(appointmentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markCheckedIn(99L, RECEPTIONIST, "VIS-2026-0001"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND);
    }

    // ================================================================== tra cứu

    @Test
    void getById_hidesOtherOwnersAppointments() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(
                appointment(1L, OTHER_OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0))));
        when(appointmentRepository.findById(2L)).thenReturn(Optional.of(
                appointment(2L, OWNER, AppointmentStatus.PENDING, TOMORROW, LocalTime.of(9, 0))));

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.APPOINTMENT_NOT_FOUND);
        assertThat(service.getById(2L).id()).isEqualTo(2L);

        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        assertThat(service.getById(1L).ownerId()).isEqualTo(OTHER_OWNER);
    }

    // ================================================================== fixtures

    private void assertCreateFails(LocalDate date, LocalTime start, ErrorCode expected) {
        assertThatThrownBy(() -> service.create(new CreateAppointmentRequest(PET, null, date, start, "Khám", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(expected);
    }

    private void stubCapacity(long doctors) {
        when(authClient.getActiveDoctorCount()).thenReturn(ApiResponse.success(doctors));
    }

    private void stubSave() {
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private AppointmentStatusHistory lastHistory() {
        ArgumentCaptor<AppointmentStatusHistory> captor = ArgumentCaptor.forClass(AppointmentStatusHistory.class);
        verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static Appointment appointment(Long id, long ownerId, AppointmentStatus status, LocalDate date, LocalTime start) {
        return Appointment.builder()
                .id(id)
                .appointmentCode("APT-TEST-" + id)
                .petId(PET)
                .ownerId(ownerId)
                .appointmentDate(date)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .reason("Khám")
                .bookingChannel(BookingChannel.ONLINE)
                .status(status)
                .build();
    }

    private static void loginAs(long userId, RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
