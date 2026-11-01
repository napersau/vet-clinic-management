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
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.PetVisitStatusHistory;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.clinical.service.DoctorStatusService;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.clinical.service.VisitSupport;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.event.VisitCheckedInEvent;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.vetclinic.clinical.testsupport.TestData.CLOCK;
import static com.vetclinic.clinical.testsupport.TestData.DOCTOR;
import static com.vetclinic.clinical.testsupport.TestData.OTHER_DOCTOR;
import static com.vetclinic.clinical.testsupport.TestData.OTHER_OWNER;
import static com.vetclinic.clinical.testsupport.TestData.OWNER;
import static com.vetclinic.clinical.testsupport.TestData.RECEPTIONIST;
import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static com.vetclinic.clinical.testsupport.TestData.dog;
import static com.vetclinic.clinical.testsupport.TestData.loginAs;
import static com.vetclinic.clinical.testsupport.TestData.pet;
import static com.vetclinic.clinical.testsupport.TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceImplTest {

    @Mock
    private PetVisitRepository visitRepository;
    @Mock
    private PetRepository petRepository;
    @Mock
    private PetVisitStatusHistoryRepository historyRepository;
    @Mock
    private AuthClient authClient;
    @Mock
    private AppointmentClient appointmentClient;
    @Mock
    private DoctorStatusService doctorStatusService;
    @Mock
    private NamedLockService lockService;
    @Mock
    private ApplicationEventPublisher publisher;

    private VisitServiceImpl service;

    @BeforeEach
    void setUp() {
        VisitSupport support = new VisitSupport(visitRepository, historyRepository);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // Khóa tên giả: chạy thẳng action, giống khi lấy được khóa
        lenient().when(lockService.runLocked(anyCollection(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
        service = new VisitServiceImpl(visitRepository, petRepository, historyRepository, authClient, appointmentClient,
                doctorStatusService, support, new VisitAssembler(), lockService, publisher, CLOCK, tm);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================== tiếp nhận (UC-19)

    @Test
    void checkIn_walkIn_createsWaitingVisitWithEmergencyFlagAndPublishesEvent() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(pet(11L, dog())));
        stubSaveVisit();

        VisitResponse response = service.checkIn(new CheckInRequest(11L, null, "  Co giật  ", true));

        assertThat(response.status()).isEqualTo(VisitStatus.WAITING);
        assertThat(response.visitCode()).isEqualTo("VIS-2026-00100");
        assertThat(response.emergency()).isTrue();
        assertThat(response.receptionistId()).isEqualTo(RECEPTIONIST);
        assertThat(response.appointmentId()).isNull();
        assertThat(response.initialSymptoms()).isEqualTo("Co giật");
        assertThat(response.doctorId()).isNull();

        PetVisitStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(history.getNotes()).contains("vãng lai").contains("CẤP CỨU");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        VisitCheckedInEvent checkedIn = (VisitCheckedInEvent) event.getValue();
        assertThat(checkedIn.visitId()).isEqualTo(100L);
        assertThat(checkedIn.appointmentId()).isNull();
        assertThat(checkedIn.emergency()).isTrue();
    }

    @Test
    void checkIn_withTodaysConfirmedAppointment_usesItsPetAndLinksTheAppointment() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(appointmentClient.getAppointment(7L)).thenReturn(ApiResponse.success(appointment("CONFIRMED", LocalDate.of(2026, 10, 30), 11L)));
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(pet(11L, dog())));
        stubSaveVisit();

        // Không gửi petId và triệu chứng: lấy từ lịch hẹn
        VisitResponse response = service.checkIn(new CheckInRequest(null, 7L, null, false));

        assertThat(response.petId()).isEqualTo(11L);
        assertThat(response.appointmentId()).isEqualTo(7L);
        assertThat(response.initialSymptoms()).isEqualTo("Khám tổng quát");
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(((VisitCheckedInEvent) event.getValue()).appointmentId()).isEqualTo(7L);
    }

    private PetVisit checkInWithSlot(LocalTime start, LocalTime end) {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(appointmentClient.getAppointment(7L)).thenReturn(ApiResponse.success(new AppointmentSummaryResponse(7L,
                "APT-20261030-00007", 11L, 5L, LocalDate.of(2026, 10, 30), start, end, "Khám", "CONFIRMED")));
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(pet(11L, dog())));
        stubSaveVisit();

        service.checkIn(new CheckInRequest(null, 7L, null, false));

        ArgumentCaptor<PetVisit> saved = ArgumentCaptor.forClass(PetVisit.class);
        verify(visitRepository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    @Test
    void checkIn_onTimeAppointment_getsPriorityTwoViaScheduledAt() {
        // Bây giờ là 09:00 giờ VN, khung hẹn 09:00-09:30
        PetVisit visit = checkInWithSlot(LocalTime.of(9, 0), LocalTime.of(9, 30));

        assertThat(visit.getScheduledAt()).isEqualTo(Instant.parse("2026-10-30T02:00:00Z"));
        assertThat(lastHistory().getNotes()).contains("đúng giờ hẹn");
    }

    @Test
    void checkIn_earlyArrivalIsStillOnTime() {
        PetVisit visit = checkInWithSlot(LocalTime.of(9, 30), LocalTime.of(10, 0));

        assertThat(visit.getScheduledAt()).isEqualTo(Instant.parse("2026-10-30T02:30:00Z"));
    }

    @Test
    void checkIn_lateForTheSlot_isQueuedLikeAWalkIn() {
        // Khung 08:00-08:30 đã qua lúc 09:00 -> mất ưu tiên có hẹn (nghiệp vụ §8.4 mục 4)
        PetVisit visit = checkInWithSlot(LocalTime.of(8, 0), LocalTime.of(8, 30));

        assertThat(visit.getScheduledAt()).isNull();
        assertThat(lastHistory().getNotes()).contains("đến trễ");
    }

    @Test
    void checkIn_appointmentThatIsNotConfirmedOrNotToday_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(appointmentClient.getAppointment(1L)).thenReturn(ApiResponse.success(appointment("PENDING", LocalDate.of(2026, 10, 30), 11L)));
        when(appointmentClient.getAppointment(2L)).thenReturn(ApiResponse.success(appointment("CONFIRMED", LocalDate.of(2026, 10, 31), 11L)));
        when(appointmentClient.getAppointment(3L)).thenReturn(ApiResponse.success(appointment("NO_SHOW", LocalDate.of(2026, 10, 30), 11L)));

        for (long id : new long[]{1L, 2L, 3L}) {
            assertError(ErrorCode.APPOINTMENT_NOT_READY_FOR_CHECKIN, () -> service.checkIn(new CheckInRequest(null, id, null, false)));
        }
        verify(visitRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkIn_petThatDoesNotMatchTheAppointment_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(appointmentClient.getAppointment(7L)).thenReturn(ApiResponse.success(appointment("CONFIRMED", LocalDate.of(2026, 10, 30), 11L)));

        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.checkIn(new CheckInRequest(99L, 7L, null, false)));
    }

    @Test
    void checkIn_petAlreadyInAnActiveVisit_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(pet(11L, dog())));
        when(visitRepository.existsByPetIdAndStatusIn(11L, VisitStatus.ACTIVE)).thenReturn(true);

        assertError(ErrorCode.VISIT_ALREADY_ACTIVE, () -> service.checkIn(new CheckInRequest(11L, null, null, false)));
        verify(visitRepository, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void checkIn_appointmentThatAlreadyHasAVisit_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(visitRepository.existsByAppointmentId(7L)).thenReturn(true);

        // Chặn ngay trước khi gọi sang appointment-service (lịch đã CHECKED_IN sẽ báo "chưa sẵn sàng" gây khó hiểu)
        assertError(ErrorCode.VISIT_ALREADY_ACTIVE, () -> service.checkIn(new CheckInRequest(null, 7L, null, false)));
        verifyNoInteractions(appointmentClient);
    }

    @Test
    void checkIn_withNeitherPetNorAppointment_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);

        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.checkIn(new CheckInRequest(null, null, null, false)));
    }

    @Test
    void checkIn_unknownPet_isPetNotFound() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(petRepository.findWithSpeciesById(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.PET_NOT_FOUND, () -> service.checkIn(new CheckInRequest(99L, null, null, false)));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    // ================================================================== phân công Bác sĩ (UC-21, EX-05)

    @Test
    void assignDoctor_toAvailableDoctor_setsDoctorAndRoomButKeepsWaiting() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(DOCTOR, RoleCode.VETERINARIAN, "ACTIVE", "P01");
        PetVisit visit = visit(100L, VisitStatus.WAITING, null);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit));
        when(doctorStatusService.statusOf(DOCTOR)).thenReturn(DoctorStatus.AVAILABLE);

        VisitResponse response = service.assignDoctor(100L, new AssignDoctorRequest(DOCTOR));

        assertThat(response.doctorId()).isEqualTo(DOCTOR);
        assertThat(response.assignedRoom()).isEqualTo("P01");
        assertThat(response.status()).isEqualTo(VisitStatus.WAITING);
        PetVisitStatusHistory history = lastHistory();
        assertThat(history.getFromStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(history.getToStatus()).isEqualTo(VisitStatus.WAITING);
        assertThat(history.getNotes()).contains("Phân công").contains("P01");
        verify(lockService).runLocked(eq(List.of("vetclinic:doctor:" + DOCTOR)), any());   // khóa đúng theo bác sĩ
    }

    @Test
    void assignDoctor_toBusyDoctor_isRefused() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(DOCTOR, RoleCode.VETERINARIAN, "ACTIVE", "P01");
        PetVisit visit = visit(100L, VisitStatus.WAITING, null);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit));
        when(doctorStatusService.statusOf(DOCTOR)).thenReturn(DoctorStatus.BUSY);

        assertError(ErrorCode.DOCTOR_NOT_AVAILABLE, () -> service.assignDoctor(100L, new AssignDoctorRequest(DOCTOR)));
        assertThat(visit.getDoctorId()).isNull();
    }

    @Test
    void assignDoctor_toDoctorWithResultsWaiting_isRefusedByPriorityLock() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(DOCTOR, RoleCode.VETERINARIAN, "ACTIVE", "P01");
        PetVisit visit = visit(100L, VisitStatus.WAITING, null);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit));
        when(doctorStatusService.statusOf(DOCTOR)).thenReturn(DoctorStatus.AVAILABLE_PRIORITY_CALLBACK);

        assertError(ErrorCode.DOCTOR_PRIORITY_LOCKED, () -> service.assignDoctor(100L, new AssignDoctorRequest(DOCTOR)));
        assertThat(visit.getDoctorId()).isNull();
    }

    @Test
    void assignDoctor_requiresAnActiveVeterinarian() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(30L, RoleCode.LAB_TECHNICIAN, "ACTIVE", null);
        stubDoctor(31L, RoleCode.VETERINARIAN, "LOCKED", "P03");

        assertError(ErrorCode.INVALID_DOCTOR, () -> service.assignDoctor(100L, new AssignDoctorRequest(30L)));
        assertError(ErrorCode.INVALID_DOCTOR, () -> service.assignDoctor(100L, new AssignDoctorRequest(31L)));
        verify(lockService, never()).runLocked(anyCollection(), any());
    }

    @Test
    void assignDoctor_onlyForWaitingVisits_andNotTheSameDoctorTwice() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(DOCTOR, RoleCode.VETERINARIAN, "ACTIVE", "P01");
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit(100L, VisitStatus.IN_PROGRESS, OTHER_DOCTOR)));
        when(visitRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(visit(101L, VisitStatus.WAITING, DOCTOR)));

        assertError(ErrorCode.INVALID_STATUS_TRANSITION, () -> service.assignDoctor(100L, new AssignDoctorRequest(DOCTOR)));
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.assignDoctor(101L, new AssignDoctorRequest(DOCTOR)));
    }

    @Test
    void assignDoctor_reassigningToAnotherAvailableDoctorIsRecordedAsAChange() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubDoctor(OTHER_DOCTOR, RoleCode.VETERINARIAN, "ACTIVE", "P02");
        PetVisit visit = visit(100L, VisitStatus.WAITING, DOCTOR);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit));
        when(doctorStatusService.statusOf(OTHER_DOCTOR)).thenReturn(DoctorStatus.AVAILABLE);

        service.assignDoctor(100L, new AssignDoctorRequest(OTHER_DOCTOR));

        assertThat(visit.getDoctorId()).isEqualTo(OTHER_DOCTOR);
        assertThat(visit.getAssignedRoom()).isEqualTo("P02");
        assertThat(lastHistory().getNotes()).startsWith("Đổi sang bác sĩ");
    }

    // ================================================================== tra cứu

    @Test
    void getById_hidesAnotherOwnersVisit() {
        PetVisit visit = visit(100L, VisitStatus.WAITING, null);
        visit.getPet().setOwnerId(OTHER_OWNER);
        when(visitRepository.findWithPetById(100L)).thenReturn(Optional.of(visit));

        loginAs(OWNER, RoleCode.PET_OWNER);
        assertError(ErrorCode.VISIT_NOT_FOUND, () -> service.getById(100L));

        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        assertThat(service.getById(100L).visitCode()).isEqualTo("VIS-2026-100");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_returnsPagedVisits_newestFirst_andClampsPageSize() {
        PetVisit visit = visit(100L, VisitStatus.WAITING, DOCTOR);
        when(visitRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visit), PageRequest.of(0, 100), 1));

        var page = service.search(VisitStatus.WAITING, LocalDate.of(2026, 10, 30), DOCTOR, null, "vis-", -3, 5000);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).status()).isEqualTo(VisitStatus.WAITING);
        assertThat(page.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(visitRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();          // trang âm bị đưa về 0
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);      // kích thước tối đa 100
        assertThat(pageable.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    // ================================================================== hủy đợt khám

    @Test
    void cancel_waitingVisit_byReceptionist() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        PetVisit visit = visit(100L, VisitStatus.WAITING, null);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit));

        VisitResponse response = service.cancel(100L, new ReasonRequest("  Khách bận việc xin về  "));

        assertThat(response.status()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("Khách bận việc xin về");
        assertThat(response.locked()).isTrue();
        assertThat(lastHistory().getToStatus()).isEqualTo(VisitStatus.CANCELLED);
    }

    @Test
    void cancel_permissionsFollowTheStatus() {
        // Bác sĩ không hủy được ca đang chờ ở sảnh; Lễ tân không hủy được ca đang khám
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit(100L, VisitStatus.WAITING, DOCTOR)));
        assertError(ErrorCode.ACCESS_DENIED, () -> service.cancel(100L, new ReasonRequest("x")));

        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(visitRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(visit(101L, VisitStatus.IN_PROGRESS, DOCTOR)));
        assertError(ErrorCode.ACCESS_DENIED, () -> service.cancel(101L, new ReasonRequest("x")));

        // Ca đã chốt hoặc đã hoàn tất / đã hủy thì không hủy được nữa
        when(visitRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(visit(102L, VisitStatus.WAITING_PAYMENT, DOCTOR)));
        assertError(ErrorCode.INVALID_STATUS_TRANSITION, () -> service.cancel(102L, new ReasonRequest("x")));
        when(visitRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(visit(103L, VisitStatus.CANCELLED, null)));
        assertError(ErrorCode.INVALID_STATUS_TRANSITION, () -> service.cancel(103L, new ReasonRequest("x")));
    }

    @Test
    void cancel_inProgress_byTheAssignedDoctorOnly() {
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(visit(100L, VisitStatus.IN_PROGRESS, DOCTOR)));

        loginAs(OTHER_DOCTOR, RoleCode.VETERINARIAN);
        assertError(ErrorCode.VISIT_NOT_ASSIGNED_TO_YOU, () -> service.cancel(100L, new ReasonRequest("x")));

        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        assertThat(service.cancel(100L, new ReasonRequest("Chuyển viện cấp cứu")).status()).isEqualTo(VisitStatus.CANCELLED);
    }

    // ================================================================== fixtures

    private void stubSaveVisit() {
        when(visitRepository.saveAndFlush(any(PetVisit.class))).thenAnswer(inv -> {
            PetVisit saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });
    }

    private void stubDoctor(long id, RoleCode role, String status, String room) {
        when(authClient.getUser(id)).thenReturn(ApiResponse.success(
                new UserSummaryResponse(id, "BS " + id, "0911111111", role, room, status)));
    }

    private static AppointmentSummaryResponse appointment(String status, LocalDate date, Long petId) {
        return new AppointmentSummaryResponse(7L, "APT-20261030-00007", petId, 5L, date, null, null, "Khám tổng quát", status);
    }

    private PetVisitStatusHistory lastHistory() {
        ArgumentCaptor<PetVisitStatusHistory> captor = ArgumentCaptor.forClass(PetVisitStatusHistory.class);
        verify(historyRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
