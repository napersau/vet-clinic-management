package com.vetclinic.appointment.service;

import com.vetclinic.appointment.client.AuthClient;
import com.vetclinic.appointment.config.BookingProperties;
import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.dto.internal.SlotBookedCount;
import com.vetclinic.appointment.dto.response.SlotAvailabilityResponse;
import com.vetclinic.appointment.repository.AppointmentRepository;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    static final List<String> STANDARD_SLOTS = List.of("08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00",
            "13:30", "14:00", "14:30", "15:00", "15:30", "16:00", "16:30");

    // 2026-10-30 09:00 giờ Việt Nam
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
    static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Mock
    private AuthClient authClient;
    @Mock
    private AppointmentRepository appointmentRepository;

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        BookingProperties properties = new BookingProperties("Asia/Ho_Chi_Minh", 30, STANDARD_SLOTS, 30, 2, 60, false, 5);
        slotService = new SlotService(properties, new SlotPolicy(properties, CLOCK, authClient), appointmentRepository);
    }

    @Test
    void getSlots_returnsAll14SlotsWithCapacityEqualToActiveDoctors() {
        when(authClient.getActiveDoctorCount()).thenReturn(ApiResponse.success(3L));
        when(appointmentRepository.countBookedBySlot(TOMORROW, AppointmentStatus.OCCUPYING)).thenReturn(List.of());

        List<SlotAvailabilityResponse> slots = slotService.getSlots(TOMORROW);

        assertThat(slots).hasSize(14);
        assertThat(slots).allSatisfy(s -> {
            assertThat(s.capacity()).isEqualTo(3);
            assertThat(s.booked()).isZero();
            assertThat(s.remaining()).isEqualTo(3);
            assertThat(s.available()).isTrue();
            assertThat(s.past()).isFalse();
        });
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(slots.get(13).startTime()).isEqualTo(LocalTime.of(16, 30));
        assertThat(slots.get(13).endTime()).isEqualTo(LocalTime.of(17, 0));
    }

    @Test
    void getSlots_reflectsBookedCountsAndFullSlots() {
        when(authClient.getActiveDoctorCount()).thenReturn(ApiResponse.success(2L));
        when(appointmentRepository.countBookedBySlot(TOMORROW, AppointmentStatus.OCCUPYING)).thenReturn(List.of(
                new SlotBookedCount(LocalTime.of(9, 0), 1L),
                new SlotBookedCount(LocalTime.of(9, 30), 2L),
                new SlotBookedCount(LocalTime.of(10, 0), 5L)));   // dữ liệu vượt sức chứa (vd. bác sĩ bị khóa sau khi đã có hẹn)

        List<SlotAvailabilityResponse> slots = slotService.getSlots(TOMORROW);

        SlotAvailabilityResponse nineOclock = slotAt(slots, 9, 0);
        assertThat(nineOclock.booked()).isEqualTo(1);
        assertThat(nineOclock.remaining()).isEqualTo(1);
        assertThat(nineOclock.available()).isTrue();

        SlotAvailabilityResponse full = slotAt(slots, 9, 30);
        assertThat(full.remaining()).isZero();
        assertThat(full.available()).isFalse();
        assertThat(full.past()).isFalse();

        SlotAvailabilityResponse overbooked = slotAt(slots, 10, 0);
        assertThat(overbooked.remaining()).isZero();   // không bao giờ âm
        assertThat(overbooked.available()).isFalse();
    }

    @Test
    void getSlots_withNoActiveDoctors_marksEverySlotUnavailable() {
        when(authClient.getActiveDoctorCount()).thenReturn(ApiResponse.success(0L));
        when(appointmentRepository.countBookedBySlot(TOMORROW, AppointmentStatus.OCCUPYING)).thenReturn(List.of());

        assertThat(slotService.getSlots(TOMORROW)).allSatisfy(s -> {
            assertThat(s.capacity()).isZero();
            assertThat(s.available()).isFalse();
        });
    }

    @Test
    void getSlots_forToday_marksStartedSlotsAsPast() {
        when(authClient.getActiveDoctorCount()).thenReturn(ApiResponse.success(2L));
        when(appointmentRepository.countBookedBySlot(TODAY, AppointmentStatus.OCCUPYING)).thenReturn(List.of());

        List<SlotAvailabilityResponse> slots = slotService.getSlots(TODAY);

        // Bây giờ là 09:00: các khung 08:00, 08:30, 09:00 đã bắt đầu
        assertThat(slots.stream().filter(SlotAvailabilityResponse::past).map(SlotAvailabilityResponse::startTime))
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0));
        assertThat(slotAt(slots, 9, 0).available()).isFalse();
        assertThat(slotAt(slots, 9, 30).available()).isTrue();
    }

    @Test
    void getSlots_rejectsPastDateAndDateBeyondBookingHorizon() {
        assertThatThrownBy(() -> slotService.getSlots(TODAY.minusDays(1)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
        assertThatThrownBy(() -> slotService.getSlots(TODAY.plusDays(61)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_DATA);
    }

    @Test
    void getSlots_whenAuthServiceReturnsNothing_failsAsServiceUnavailable() {
        when(authClient.getActiveDoctorCount()).thenReturn(null);

        assertThatThrownBy(() -> slotService.getSlots(TOMORROW))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void properties_failFastOnBrokenConfiguration() {
        assertThatThrownBy(() -> new BookingProperties("Asia/Ho_Chi_Minh", 30, List.of("08:00", "25:99"), 30, 2, 60, false, 5))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BookingProperties("Khong/Co", 30, STANDARD_SLOTS, 30, 2, 60, false, 5))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BookingProperties("Asia/Ho_Chi_Minh", 30, List.of(), 30, 2, 60, false, 5))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SlotAvailabilityResponse slotAt(List<SlotAvailabilityResponse> slots, int hour, int minute) {
        return slots.stream().filter(s -> s.startTime().equals(LocalTime.of(hour, minute))).findFirst().orElseThrow();
    }
}
