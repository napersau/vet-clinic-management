package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.PetGender;
import com.vetclinic.clinical.dto.request.CreatePetRequest;
import com.vetclinic.clinical.dto.request.UpdatePetRequest;
import com.vetclinic.clinical.dto.response.PetResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.mapper.ClinicalMapperImpl;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.SpeciesRepository;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.vetclinic.clinical.testsupport.TestData.CLOCK;
import static com.vetclinic.clinical.testsupport.TestData.DOCTOR;
import static com.vetclinic.clinical.testsupport.TestData.OTHER_OWNER;
import static com.vetclinic.clinical.testsupport.TestData.OWNER;
import static com.vetclinic.clinical.testsupport.TestData.RECEPTIONIST;
import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static com.vetclinic.clinical.testsupport.TestData.cat;
import static com.vetclinic.clinical.testsupport.TestData.dog;
import static com.vetclinic.clinical.testsupport.TestData.loginAs;
import static com.vetclinic.clinical.testsupport.TestData.pet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PetServiceImplTest {

    @Mock
    private PetRepository petRepository;
    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private AuthClient authClient;

    private PetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PetServiceImpl(petRepository, speciesRepository, authClient, new ClinicalMapperImpl(), CLOCK,
                mock(PlatformTransactionManager.class));
        lenient().when(speciesRepository.findById(1L)).thenReturn(Optional.of(dog()));
        lenient().when(petRepository.saveAndFlush(any(Pet.class))).thenAnswer(inv -> {
            Pet saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(11L);
            }
            return saved;
        });
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static CreatePetRequest newPet(Long ownerId, Long speciesId) {
        return new CreatePetRequest(ownerId, speciesId, "  Lu ", null, null, null, null, new BigDecimal("4.50"), null, null, null);
    }

    private static UpdatePetRequest rename(String name) {
        return new UpdatePetRequest(name, null, null, null, null, null, null, null, null);
    }

    private void ownerLookupReturns(RoleCode role, String status) {
        when(authClient.getUser(OWNER)).thenReturn(ApiResponse.success(
                new UserSummaryResponse(OWNER, "Chủ nuôi", "0901234567", role, null, status)));
    }

    // ================================================================== thêm hồ sơ (UC-12, UC-18)

    @Test
    void create_byOwnerAlwaysUsesTheTokenOwnerAndGeneratesACodeFromTheId() {
        loginAs(OWNER, RoleCode.PET_OWNER);

        PetResponse response = service.create(newPet(OTHER_OWNER, 1L));   // ownerId trong body bị bỏ qua

        ArgumentCaptor<Pet> saved = ArgumentCaptor.forClass(Pet.class);
        verify(petRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(OWNER);
        assertThat(saved.getValue().getName()).isEqualTo("Lu");
        assertThat(saved.getValue().getGender()).isEqualTo(PetGender.UNKNOWN);   // không gửi giới tính
        assertThat(saved.getValue().isNeutered()).isFalse();                     // Boolean null -> false
        assertThat(response.petCode()).isEqualTo("PET-2026-00011");            // năm theo Clock phòng khám
        assertThat(response.speciesCode()).isEqualTo("DOG");
        verifyNoInteractions(authClient);
    }

    @Test
    void create_byReceptionistRequiresAnActivePetOwner() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.create(newPet(null, 1L)));

        ownerLookupReturns(RoleCode.VETERINARIAN, "ACTIVE");
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.create(newPet(OWNER, 1L)));

        ownerLookupReturns(RoleCode.PET_OWNER, "LOCKED");
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.create(newPet(OWNER, 1L)));
        verify(petRepository, never()).saveAndFlush(any());

        ownerLookupReturns(RoleCode.PET_OWNER, "ACTIVE");
        assertThat(service.create(newPet(OWNER, 1L)).ownerId()).isEqualTo(OWNER);
    }

    @Test
    void create_byReceptionistForAnUnknownUserIsUserNotFound() {
        loginAs(RECEPTIONIST, RoleCode.RECEPTIONIST);
        when(authClient.getUser(OWNER)).thenReturn(ApiResponse.success(null));

        assertError(ErrorCode.USER_NOT_FOUND, () -> service.create(newPet(OWNER, 1L)));
    }

    @Test
    void create_refusesOtherRolesAndUnknownSpecies() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.create(newPet(null, 1L)));

        loginAs(OWNER, RoleCode.PET_OWNER);
        when(speciesRepository.findById(99L)).thenReturn(Optional.empty());
        assertError(ErrorCode.SPECIES_NOT_FOUND, () -> service.create(newPet(null, 99L)));
        verify(petRepository, never()).saveAndFlush(any());
    }

    // ================================================================== xem / sửa

    @Test
    void getById_hidesAnotherOwnersPetButStaffCanSeeIt() {
        Pet othersPet = pet(11L, cat());
        othersPet.setOwnerId(OTHER_OWNER);
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(othersPet));

        loginAs(OWNER, RoleCode.PET_OWNER);
        assertError(ErrorCode.PET_NOT_FOUND, () -> service.getById(11L));   // không lộ sự tồn tại

        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        assertThat(service.getById(11L).speciesName()).isEqualTo("Mèo");
    }

    @Test
    void update_changesOnlyTheGivenFieldsOfYourOwnPet() {
        loginAs(OWNER, RoleCode.PET_OWNER);
        Pet mine = pet(11L, dog());
        mine.setBreed("Poodle");
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(mine));

        service.update(11L, rename(" Mực "));

        assertThat(mine.getName()).isEqualTo("Mực");
        assertThat(mine.getBreed()).isEqualTo("Poodle");   // null = giữ nguyên
        assertThat(mine.getGender()).isEqualTo(PetGender.MALE);
    }

    @Test
    void update_refusesAnotherOwnersPetAndRolesOtherThanOwnerOrReceptionist() {
        Pet othersPet = pet(11L, dog());
        othersPet.setOwnerId(OTHER_OWNER);
        when(petRepository.findWithSpeciesById(11L)).thenReturn(Optional.of(othersPet));

        loginAs(OWNER, RoleCode.PET_OWNER);
        assertError(ErrorCode.PET_NOT_FOUND, () -> service.update(11L, rename("X")));

        loginAs(DOCTOR, RoleCode.VETERINARIAN);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.update(11L, rename("X")));
        assertThat(othersPet.getName()).isEqualTo("Lu");
    }

    // ================================================================== nội bộ (cho appointment-service)

    @Test
    void getSummary_returnsOwnerForTheBookingCheckOrPetNotFound() {
        when(petRepository.findById(11L)).thenReturn(Optional.of(pet(11L, dog())));
        when(petRepository.findById(12L)).thenReturn(Optional.empty());

        assertThat(service.getSummary(11L).ownerId()).isEqualTo(OWNER);
        assertError(ErrorCode.PET_NOT_FOUND, () -> service.getSummary(12L));
    }

    @Test
    void getSummaries_skipsUnknownIdsAndAnswersAnEmptyListWithoutQuerying() {
        when(petRepository.findAllById(List.of(11L, 99L))).thenReturn(List.of(pet(11L, dog())));

        assertThat(service.getSummaries(List.of(11L, 99L))).extracting("id").containsExactly(11L);
        assertThat(service.getSummaries(List.of())).isEmpty();
        verify(petRepository, never()).findAllById(List.of());
    }
}
