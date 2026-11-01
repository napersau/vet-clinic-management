package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.dto.request.LabTestItemRequest;
import com.vetclinic.clinical.dto.request.ReferenceRangeInput;
import com.vetclinic.clinical.dto.request.ReferenceRangeRequest;
import com.vetclinic.clinical.dto.request.UpdateLabTestItemRequest;
import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.entity.LabTestItem;
import com.vetclinic.clinical.entity.LabTestReferenceRange;
import com.vetclinic.clinical.entity.ServiceLabTestItem;
import com.vetclinic.clinical.mapper.ClinicalMapper;
import com.vetclinic.clinical.repository.ClinicServiceRepository;
import com.vetclinic.clinical.repository.LabTestItemRepository;
import com.vetclinic.clinical.repository.LabTestReferenceRangeRepository;
import com.vetclinic.clinical.repository.ServiceLabTestItemRepository;
import com.vetclinic.clinical.repository.SpeciesRepository;
import com.vetclinic.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static com.vetclinic.clinical.testsupport.TestData.dog;
import static com.vetclinic.clinical.testsupport.TestData.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {

    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private ClinicServiceRepository serviceRepository;
    @Mock
    private LabTestItemRepository labTestItemRepository;
    @Mock
    private LabTestReferenceRangeRepository rangeRepository;
    @Mock
    private ServiceLabTestItemRepository serviceLabItemRepository;

    private CatalogServiceImpl catalog;

    @BeforeEach
    void setUp() {
        catalog = new CatalogServiceImpl(speciesRepository, serviceRepository, labTestItemRepository, rangeRepository,
                serviceLabItemRepository, mock(ClinicalMapper.class));
        lenient().when(labTestItemRepository.saveAndFlush(any(LabTestItem.class))).thenAnswer(inv -> {
            LabTestItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(10L);
            }
            return item;
        });
        lenient().when(rangeRepository.findByLabTestItemIdIn(anyList())).thenReturn(List.of());
    }

    private static LabTestItem item(long id, String code) {
        return LabTestItem.builder().id(id).code(code).name(code).unit("g/dL").build();
    }

    // ================================================================== chỉ số xét nghiệm (UC-05, UC-06)

    @Test
    void createLabTestItem_normalizesCodeAndStoresRangesPerSpecies() {
        when(labTestItemRepository.existsByCode("HCT")).thenReturn(false);
        when(speciesRepository.findById(1L)).thenReturn(Optional.of(dog()));

        catalog.createLabTestItem(new LabTestItemRequest(" hct ", "Hematocrit", "%", null,
                List.of(new ReferenceRangeInput(1L, new BigDecimal("37.00"), new BigDecimal("55.00")))));

        ArgumentCaptor<LabTestItem> item = ArgumentCaptor.forClass(LabTestItem.class);
        verify(labTestItemRepository).saveAndFlush(item.capture());
        assertThat(item.getValue().getCode()).isEqualTo("HCT");
        ArgumentCaptor<LabTestReferenceRange> range = ArgumentCaptor.forClass(LabTestReferenceRange.class);
        verify(rangeRepository).save(range.capture());
        assertThat(range.getValue().getMinValue()).isEqualByComparingTo("37.00");
    }

    @Test
    void createLabTestItem_refusesDuplicateCodeAndBadRanges() {
        when(labTestItemRepository.existsByCode("WBC")).thenReturn(true);
        assertError(ErrorCode.CODE_ALREADY_EXISTS,
                () -> catalog.createLabTestItem(new LabTestItemRequest("wbc", "Bạch cầu", "10^9/L", null, null)));

        // min >= max
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> catalog.createLabTestItem(new LabTestItemRequest("HCT", "Hct", "%", null,
                List.of(new ReferenceRangeInput(1L, new BigDecimal("55.00"), new BigDecimal("55.00"))))));
        // hai khoảng cho cùng một loài
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> catalog.createLabTestItem(new LabTestItemRequest("HCT", "Hct", "%", null,
                List.of(new ReferenceRangeInput(1L, new BigDecimal("1.00"), new BigDecimal("2.00")),
                        new ReferenceRangeInput(1L, new BigDecimal("3.00"), new BigDecimal("4.00"))))));
        verify(labTestItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void createLabTestItem_unknownSpeciesIsRefused() {
        when(labTestItemRepository.existsByCode("HCT")).thenReturn(false);
        when(speciesRepository.findById(99L)).thenReturn(Optional.empty());

        assertError(ErrorCode.SPECIES_NOT_FOUND, () -> catalog.createLabTestItem(new LabTestItemRequest("HCT", "Hct", "%", null,
                List.of(new ReferenceRangeInput(99L, new BigDecimal("1.00"), new BigDecimal("2.00"))))));
    }

    @Test
    void updateLabTestItem_changesNameUnitDescriptionButNotTheCode() {
        LabTestItem wbc = item(1L, "WBC");
        when(labTestItemRepository.findById(1L)).thenReturn(Optional.of(wbc));

        LabTestItemResponse response = catalog.updateLabTestItem(1L, new UpdateLabTestItemRequest("Bạch cầu", "10^9/L", "  Mô tả "));

        assertThat(response.code()).isEqualTo("WBC");
        assertThat(response.name()).isEqualTo("Bạch cầu");
        assertThat(response.description()).isEqualTo("Mô tả");
        assertError(ErrorCode.LAB_ITEM_NOT_FOUND,
                () -> catalog.updateLabTestItem(404L, new UpdateLabTestItemRequest("x", "y", null)));
    }

    @Test
    void saveReferenceRange_updatesTheExistingRangeInsteadOfAddingASecond() {
        LabTestItem wbc = item(1L, "WBC");
        LabTestReferenceRange existing = LabTestReferenceRange.builder().labTestItem(wbc).species(dog())
                .minValue(new BigDecimal("6.00")).maxValue(new BigDecimal("17.00")).build();
        when(labTestItemRepository.findById(1L)).thenReturn(Optional.of(wbc));
        when(speciesRepository.findById(1L)).thenReturn(Optional.of(dog()));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 1L)).thenReturn(Optional.of(existing));

        catalog.saveReferenceRange(1L, 1L, new ReferenceRangeRequest(new BigDecimal("5.00"), new BigDecimal("18.00")));

        assertThat(existing.getMinValue()).isEqualByComparingTo("5.00");
        assertThat(existing.getMaxValue()).isEqualByComparingTo("18.00");
        verify(rangeRepository).saveAndFlush(existing);
    }

    @Test
    void saveReferenceRange_minNotBelowMaxIsRefused() {
        assertError(ErrorCode.INVALID_INPUT_DATA,
                () -> catalog.saveReferenceRange(1L, 1L, new ReferenceRangeRequest(new BigDecimal("9.00"), new BigDecimal("3.00"))));
    }

    // ================================================================== chỉ số của dịch vụ

    @Test
    void setServiceLabTestItems_replacesTheMappingOfALaboratoryService() {
        when(serviceRepository.findById(5L)).thenReturn(Optional.of(service(5L, "LAB_CBC", ServiceCategory.LABORATORY, "250000.00")));
        when(labTestItemRepository.findAllById(any())).thenReturn(List.of(item(1L, "WBC"), item(4L, "PLT")));
        when(serviceLabItemRepository.findItemIdsByServiceId(5L)).thenReturn(List.of(1L, 4L));
        when(labTestItemRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(item(1L, "WBC"), item(4L, "PLT"), item(9L, "GLU")));

        List<LabTestItemResponse> result = catalog.setServiceLabTestItems(5L, List.of(1L, 4L, 4L));

        verify(serviceLabItemRepository).deleteByServiceId(5L);
        ArgumentCaptor<List<ServiceLabTestItem>> saved = ArgumentCaptor.forClass(List.class);
        verify(serviceLabItemRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ServiceLabTestItem::getLabTestItemId).containsExactly(1L, 4L);   // bỏ trùng
        assertThat(result).extracting(LabTestItemResponse::code).containsExactly("WBC", "PLT");
    }

    @Test
    void setServiceLabTestItems_refusesNonLabServicesAndUnknownItems() {
        when(serviceRepository.findById(6L)).thenReturn(Optional.of(service(6L, "IMG_US_AB", ServiceCategory.IMAGING, "200000.00")));
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> catalog.setServiceLabTestItems(6L, List.of(1L)));

        when(serviceRepository.findById(5L)).thenReturn(Optional.of(service(5L, "LAB_CBC", ServiceCategory.LABORATORY, "250000.00")));
        when(labTestItemRepository.findAllById(any())).thenReturn(List.of(item(1L, "WBC")));
        assertError(ErrorCode.LAB_ITEM_NOT_FOUND, () -> catalog.setServiceLabTestItems(5L, List.of(1L, 77L)));
        verify(serviceLabItemRepository, never()).deleteByServiceId(any());
    }

    @Test
    void listServiceLabTestItems_unconfiguredServiceMeansEveryItem() {
        when(serviceRepository.findById(5L)).thenReturn(Optional.of(service(5L, "LAB_CBC", ServiceCategory.LABORATORY, "250000.00")));
        when(serviceLabItemRepository.findItemIdsByServiceId(5L)).thenReturn(List.of());
        when(labTestItemRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(item(1L, "WBC"), item(9L, "GLU")));

        assertThat(catalog.listServiceLabTestItems(5L)).hasSize(2);
    }
}
