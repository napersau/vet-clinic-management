package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.DoctorStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.response.DoctorStatusResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorStatusServiceImplTest {

    @Mock
    private PetVisitRepository visitRepository;
    @Mock
    private AuthClient authClient;
    @InjectMocks
    private DoctorStatusServiceImpl service;

    @Test
    void statusOf_isBusyWhenExaminingAPatient() {
        when(visitRepository.existsByDoctorIdAndStatus(1L, VisitStatus.IN_PROGRESS)).thenReturn(true);

        assertThat(service.statusOf(1L)).isEqualTo(DoctorStatus.BUSY);
    }

    @Test
    void statusOf_isBusyWhenAPatientWasAssignedButNotStartedYet() {
        // Vế bổ sung so với tài liệu: nếu không, hai Lễ tân có thể cùng phân công một bác sĩ
        when(visitRepository.existsByDoctorIdAndStatus(1L, VisitStatus.IN_PROGRESS)).thenReturn(false);
        when(visitRepository.existsByDoctorIdAndStatus(1L, VisitStatus.WAITING)).thenReturn(true);

        assertThat(service.statusOf(1L)).isEqualTo(DoctorStatus.BUSY);
    }

    @Test
    void statusOf_prioritizesCallbackWhenResultsAreWaitingAndNotPostponed() {
        when(visitRepository.existsByDoctorIdAndStatusAndCallbackPostponedAtIsNull(1L, VisitStatus.RESULTS_READY)).thenReturn(true);

        assertThat(service.statusOf(1L)).isEqualTo(DoctorStatus.AVAILABLE_PRIORITY_CALLBACK);
    }

    @Test
    void statusOf_isAvailableWhenNothingIsPendingOrAllResultsWerePostponed() {
        // Ca tạm hoãn (EX-10) không còn tính vào Khóa ưu tiên nên truy vấn "chưa hoãn" trả về false
        assertThat(service.statusOf(1L)).isEqualTo(DoctorStatus.AVAILABLE);
    }

    @Test
    void statusOf_busyWinsOverPriorityCallback() {
        when(visitRepository.existsByDoctorIdAndStatus(1L, VisitStatus.IN_PROGRESS)).thenReturn(true);

        assertThat(service.statusOf(1L)).isEqualTo(DoctorStatus.BUSY);
    }

    @Test
    void board_combinesActiveDoctorsFromAuthWithTheirComputedStatus() {
        when(authClient.getActiveDoctors()).thenReturn(ApiResponse.success(List.of(
                doctor(1L, "BS A", "P01"), doctor(2L, "BS B", "P02"), doctor(3L, "BS C", "P03"))));
        when(visitRepository.findDoctorIdsWithStatusIn(List.of(VisitStatus.IN_PROGRESS, VisitStatus.WAITING))).thenReturn(Set.of(1L));
        when(visitRepository.findDoctorIdsWithUnpostponedStatus(VisitStatus.RESULTS_READY)).thenReturn(Set.of(2L));

        List<DoctorStatusResponse> board = service.board();

        assertThat(board).extracting(DoctorStatusResponse::status)
                .containsExactly(DoctorStatus.BUSY, DoctorStatus.AVAILABLE_PRIORITY_CALLBACK, DoctorStatus.AVAILABLE);
        assertThat(board).extracting(DoctorStatusResponse::roomNumber).containsExactly("P01", "P02", "P03");
    }

    @Test
    void board_failsAsServiceUnavailableWhenAuthReturnsNothing() {
        when(authClient.getActiveDoctors()).thenReturn(null);

        assertError(ErrorCode.SERVICE_UNAVAILABLE, () -> service.board());
    }

    private static UserSummaryResponse doctor(long id, String name, String room) {
        return new UserSummaryResponse(id, name, "0911111111", RoleCode.VETERINARIAN, room, "ACTIVE");
    }
}
