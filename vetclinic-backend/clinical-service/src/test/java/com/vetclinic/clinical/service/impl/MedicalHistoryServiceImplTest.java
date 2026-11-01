package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.client.BillingClient;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.response.MedicalHistoryEntry;
import com.vetclinic.clinical.dto.response.PrescriptionSummary;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.vetclinic.clinical.testsupport.TestData.DOCTOR;
import static com.vetclinic.clinical.testsupport.TestData.OWNER;
import static com.vetclinic.clinical.testsupport.TestData.RECEPTIONIST;
import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static com.vetclinic.clinical.testsupport.TestData.dog;
import static com.vetclinic.clinical.testsupport.TestData.loginAs;
import static com.vetclinic.clinical.testsupport.TestData.pet;
import static com.vetclinic.clinical.testsupport.TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalHistoryServiceImplTest {

    @Mock
    private PetRepository petRepository;
    @Mock
    private PetVisitRepository visitRepository;
    @Mock
    private VisitAssembler assembler;
    @Mock
    private AuthClient authClient;
    @Mock
    private BillingClient billingClient;

    private MedicalHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MedicalHistoryServiceImpl(petRepository, visitRepository, assembler, authClient, billingClient, tm);
        lenient().when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(pet(11L, dog())));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static VisitResponse response(long id) {
        return new VisitResponse(id, "VIS-2026-" + id, 11L, "Lu", "PET-2026-11", "Chó", OWNER, null, DOCTOR, RECEPTIONIST, "P01",
                false, VisitStatus.COMPLETED, true, false, BigDecimal.TEN, BigDecimal.TEN, null, null, "Viêm da", null, null,
                null, null, null, null, null, null, List.of(), List.of(), List.of());
    }

    private void stubVisits(long... ids) {
        List<PetVisit> visits = Arrays.stream(ids).mapToObj(i -> visit(i, VisitStatus.COMPLETED, DOCTOR)).toList();
        lenient().when(visitRepository.findByPetId(eq(11L), any(Pageable.class))).thenReturn(new PageImpl<>(visits));
        lenient().when(visitRepository.findByPetIdAndStatus(eq(11L), eq(VisitStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(visits));
        lenient().when(assembler.detail(any(PetVisit.class))).thenAnswer(inv -> response(((PetVisit) inv.getArgument(0)).getId()));
    }

    private static PrescriptionSummary prescription(long visitId) {
        return new PrescriptionSummary(3L, visitId, "DISPENSED", null, BigDecimal.TEN, null, List.of());
    }

    @Test
    void staff_seeTheTimelineWithDoctorNameAndPrescription() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        stubVisits(7L, 8L);
        when(billingClient.getPrescriptions(List.of(7L, 8L))).thenReturn(ApiResponse.success(List.of(prescription(8L))));
        when(authClient.getUsers(any())).thenReturn(ApiResponse.success(List.of(
                new UserSummaryResponse(DOCTOR, "BS Lan", "0911111111", RoleCode.VETERINARIAN, "P01", "ACTIVE"))));

        PageResponse<MedicalHistoryEntry> page = service.history(11L, 0, 10);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getItems()).extracting(MedicalHistoryEntry::doctorName).containsOnly("BS Lan");
        assertThat(page.getItems().get(0).prescription()).isNull();              // ca 7 không kê đơn
        assertThat(page.getItems().get(1).prescription().status()).isEqualTo("DISPENSED");
        verify(visitRepository).findByPetId(eq(11L), any(Pageable.class));
    }

    @Test
    void owner_seesOnlyCompletedVisitsOfOwnPet() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        stubVisits(7L);
        when(billingClient.getPrescriptions(any())).thenReturn(ApiResponse.success(List.of()));
        when(authClient.getUsers(any())).thenReturn(ApiResponse.success(List.of()));

        PageResponse<MedicalHistoryEntry> page = service.history(11L, 0, 10);

        assertThat(page.getItems()).hasSize(1);
        verify(visitRepository).findByPetIdAndStatus(eq(11L), eq(VisitStatus.COMPLETED), any(Pageable.class));
        verify(visitRepository, never()).findByPetId(any(), any(Pageable.class));
    }

    @Test
    void owner_ofAnotherPetGetsNotFound() {
        loginAs(OWNER + 1, RoleCode.PET_OWNER);

        assertError(ErrorCode.PET_NOT_FOUND, () -> service.history(11L, 0, 10));
        verify(visitRepository, never()).findByPetIdAndStatus(any(), any(), any(Pageable.class));
    }

    @Test
    void unknownPet() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);

        assertError(ErrorCode.PET_NOT_FOUND, () -> service.history(404L, 0, 10));
    }

    @Test
    void billingOrAuthDown_stillReturnsTheMedicalRecord() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        stubVisits(7L);
        when(billingClient.getPrescriptions(any())).thenThrow(new RuntimeException("billing down"));
        when(authClient.getUsers(any())).thenThrow(new RuntimeException("auth down"));

        PageResponse<MedicalHistoryEntry> page = service.history(11L, 0, 10);

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).prescription()).isNull();
        assertThat(page.getItems().get(0).doctorName()).isNull();
        assertThat(page.getItems().get(0).visit().diagnosis()).isEqualTo("Viêm da");
    }

    @Test
    void emptyHistory_makesNoExternalCalls() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(visitRepository.findByPetId(eq(11L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.history(11L, 0, 10).getItems()).isEmpty();
        verify(billingClient, never()).getPrescriptions(any());
        verify(authClient, never()).getUsers(any());
    }
}
