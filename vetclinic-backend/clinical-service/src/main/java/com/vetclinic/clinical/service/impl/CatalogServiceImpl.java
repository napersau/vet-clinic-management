package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.dto.request.CreateServiceRequest;
import com.vetclinic.clinical.dto.request.LabTestItemRequest;
import com.vetclinic.clinical.dto.request.ReferenceRangeInput;
import com.vetclinic.clinical.dto.request.ReferenceRangeRequest;
import com.vetclinic.clinical.dto.request.UpdateLabTestItemRequest;
import com.vetclinic.clinical.dto.request.SpeciesRequest;
import com.vetclinic.clinical.dto.request.UpdateServiceRequest;
import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.dto.response.ReferenceRangeResponse;
import com.vetclinic.clinical.dto.response.ServiceResponse;
import com.vetclinic.clinical.dto.response.SpeciesResponse;
import com.vetclinic.clinical.entity.ClinicService;
import com.vetclinic.clinical.entity.LabTestItem;
import com.vetclinic.clinical.entity.LabTestReferenceRange;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.entity.ServiceLabTestItem;
import com.vetclinic.clinical.entity.Species;
import com.vetclinic.clinical.mapper.ClinicalMapper;
import com.vetclinic.clinical.repository.ClinicServiceRepository;
import com.vetclinic.clinical.repository.LabTestItemRepository;
import com.vetclinic.clinical.repository.LabTestReferenceRangeRepository;
import com.vetclinic.clinical.repository.ServiceLabTestItemRepository;
import com.vetclinic.clinical.repository.SpeciesRepository;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import com.vetclinic.clinical.service.CatalogService;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final SpeciesRepository speciesRepository;
    private final ClinicServiceRepository serviceRepository;
    private final LabTestItemRepository labTestItemRepository;
    private final LabTestReferenceRangeRepository rangeRepository;
    private final ServiceLabTestItemRepository serviceLabItemRepository;
    private final ClinicalMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<SpeciesResponse> listSpecies() {
        return speciesRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public SpeciesResponse createSpecies(SpeciesRequest request) {
        String code = normalizeCode(request.code());
        if (speciesRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.CODE_ALREADY_EXISTS, "Mã loài đã tồn tại");
        }
        Species species = Species.builder()
                .code(code).name(request.name().trim()).description(blankToNull(request.description())).build();
        return mapper.toResponse(speciesRepository.saveAndFlush(species));
    }

    @Override
    @Transactional
    public SpeciesResponse updateSpecies(Long id, SpeciesRequest request) {
        Species species = speciesRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
        String code = normalizeCode(request.code());
        if (speciesRepository.existsByCodeAndIdNot(code, id)) {
            throw new AppException(ErrorCode.CODE_ALREADY_EXISTS, "Mã loài đã tồn tại");
        }
        species.setCode(code);
        species.setName(request.name().trim());
        species.setDescription(blankToNull(request.description()));
        return mapper.toResponse(speciesRepository.saveAndFlush(species));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(boolean activeOnly) {
        List<ClinicService> services = activeOnly
                ? serviceRepository.findByActiveTrueOrderByCategoryAscNameAsc()
                : serviceRepository.findAllByOrderByCategoryAscNameAsc();
        return services.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ServiceResponse createService(CreateServiceRequest request) {
        String code = normalizeCode(request.code());
        if (serviceRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.CODE_ALREADY_EXISTS, "Mã dịch vụ đã tồn tại");
        }
        ClinicService service = ClinicService.builder()
                .code(code)
                .name(request.name().trim())
                .category(request.category())
                .price(request.price())
                .description(blankToNull(request.description()))
                .active(true)
                .build();
        return mapper.toResponse(serviceRepository.saveAndFlush(service));
    }

    @Override
    @Transactional
    public ServiceResponse updateService(Long id, UpdateServiceRequest request) {
        ClinicService service = serviceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        if (request.name() != null) {
            service.setName(request.name().trim());
        }
        if (request.price() != null) {
            service.setPrice(request.price());
        }
        if (request.description() != null) {
            service.setDescription(blankToNull(request.description()));
        }
        if (request.active() != null) {
            service.setActive(request.active());
        }
        return mapper.toResponse(serviceRepository.saveAndFlush(service));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestItemResponse> listLabTestItems() {
        List<LabTestItem> items = labTestItemRepository.findAllByOrderByCodeAsc();
        Map<Long, List<ReferenceRangeResponse>> ranges = items.isEmpty() ? Map.of()
                : rangeRepository.findByLabTestItemIdIn(items.stream().map(LabTestItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getLabTestItem().getId(),
                        Collectors.mapping(this::toRange, Collectors.toList())));
        return items.stream().map(i -> new LabTestItemResponse(i.getId(), i.getCode(), i.getName(), i.getUnit(),
                i.getDescription(), ranges.getOrDefault(i.getId(), List.of()))).toList();
    }

    @Override
    @Transactional
    public LabTestItemResponse createLabTestItem(LabTestItemRequest request) {
        String code = normalizeCode(request.code());
        if (labTestItemRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.CODE_ALREADY_EXISTS, "Mã chỉ số đã tồn tại");
        }
        List<ReferenceRangeInput> ranges = request.ranges() == null ? List.of() : request.ranges();
        Set<Long> speciesIds = new LinkedHashSet<>();
        for (ReferenceRangeInput range : ranges) {
            requireValidRange(range.minValue(), range.maxValue());
            if (!speciesIds.add(range.speciesId())) {
                throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Mỗi loài chỉ được khai báo một khoảng tham chiếu");
            }
        }
        LabTestItem item;
        try {
            item = labTestItemRepository.saveAndFlush(LabTestItem.builder().code(code).name(request.name().trim())
                    .unit(request.unit().trim()).description(blankToNull(request.description())).build());
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CODE_ALREADY_EXISTS, "Mã chỉ số đã tồn tại");
        }
        for (ReferenceRangeInput range : ranges) {
            Species species = speciesRepository.findById(range.speciesId())
                    .orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
            rangeRepository.save(LabTestReferenceRange.builder().labTestItem(item).species(species)
                    .minValue(range.minValue()).maxValue(range.maxValue()).build());
        }
        return itemResponse(item);
    }

    @Override
    @Transactional
    public LabTestItemResponse updateLabTestItem(Long id, UpdateLabTestItemRequest request) {
        LabTestItem item = labTestItemRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.LAB_ITEM_NOT_FOUND));
        item.setName(request.name().trim());
        item.setUnit(request.unit().trim());
        item.setDescription(blankToNull(request.description()));
        return itemResponse(labTestItemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public LabTestItemResponse saveReferenceRange(Long labTestItemId, Long speciesId, ReferenceRangeRequest request) {
        requireValidRange(request.minValue(), request.maxValue());
        LabTestItem item = labTestItemRepository.findById(labTestItemId)
                .orElseThrow(() -> new AppException(ErrorCode.LAB_ITEM_NOT_FOUND));
        Species species = speciesRepository.findById(speciesId).orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
        LabTestReferenceRange range = rangeRepository.findByLabTestItemIdAndSpeciesId(labTestItemId, speciesId)
                .orElseGet(() -> LabTestReferenceRange.builder().labTestItem(item).species(species).build());
        range.setMinValue(request.minValue());
        range.setMaxValue(request.maxValue());
        rangeRepository.saveAndFlush(range);
        return itemResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabTestItemResponse> listServiceLabTestItems(Long serviceId) {
        requireLabService(serviceId);
        return itemsOfService(serviceId);
    }

    @Override
    @Transactional
    public List<LabTestItemResponse> setServiceLabTestItems(Long serviceId, List<Long> labTestItemIds) {
        requireLabService(serviceId);
        Set<Long> ids = new LinkedHashSet<>(labTestItemIds);
        if (labTestItemRepository.findAllById(ids).size() != ids.size()) {
            throw new AppException(ErrorCode.LAB_ITEM_NOT_FOUND);
        }
        serviceLabItemRepository.deleteByServiceId(serviceId);
        serviceLabItemRepository.saveAll(ids.stream().map(itemId -> new ServiceLabTestItem(serviceId, itemId)).toList());
        return itemsOfService(serviceId);
    }

    private void requireLabService(Long serviceId) {
        ClinicService service = serviceRepository.findById(serviceId).orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        if (service.getCategory() != ServiceCategory.LABORATORY) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Chỉ dịch vụ xét nghiệm (LABORATORY) mới có danh sách chỉ số");
        }
    }

    /** Chưa cấu hình dòng nào thì dịch vụ được coi là gồm mọi chỉ số (tương thích ngược). */
    private List<LabTestItemResponse> itemsOfService(Long serviceId) {
        Set<Long> configured = new LinkedHashSet<>(serviceLabItemRepository.findItemIdsByServiceId(serviceId));
        return listLabTestItems().stream().filter(i -> configured.isEmpty() || configured.contains(i.id())).toList();
    }

    private LabTestItemResponse itemResponse(LabTestItem item) {
        List<ReferenceRangeResponse> ranges = rangeRepository.findByLabTestItemIdIn(List.of(item.getId())).stream().map(this::toRange).toList();
        return new LabTestItemResponse(item.getId(), item.getCode(), item.getName(), item.getUnit(), item.getDescription(), ranges);
    }

    private static void requireValidRange(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) >= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Giá trị ngưỡng dưới phải nhỏ hơn ngưỡng trên");
        }
    }

    private ReferenceRangeResponse toRange(LabTestReferenceRange r) {
        return new ReferenceRangeResponse(r.getSpecies().getId(), r.getSpecies().getCode(), r.getMinValue(), r.getMaxValue());
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
