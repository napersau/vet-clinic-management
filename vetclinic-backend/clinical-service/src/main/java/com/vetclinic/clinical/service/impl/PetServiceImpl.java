package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.PetGender;
import com.vetclinic.clinical.dto.request.CreatePetRequest;
import com.vetclinic.clinical.dto.request.UpdatePetRequest;
import com.vetclinic.clinical.dto.response.PetResponse;
import com.vetclinic.clinical.dto.response.PetSummaryResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.Species;
import com.vetclinic.clinical.mapper.ClinicalMapper;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.SpeciesRepository;
import com.vetclinic.clinical.service.PetService;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PetServiceImpl implements PetService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PetRepository petRepository;
    private final SpeciesRepository speciesRepository;
    private final PetVisitRepository visitRepository;
    private final AuthClient authClient;
    private final ClinicalMapper mapper;
    private final VisitAssembler assembler;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PetServiceImpl(PetRepository petRepository, SpeciesRepository speciesRepository,
                          PetVisitRepository visitRepository, AuthClient authClient, ClinicalMapper mapper,
                          VisitAssembler assembler, Clock clock, PlatformTransactionManager transactionManager) {
        this.petRepository = petRepository;
        this.speciesRepository = speciesRepository;
        this.visitRepository = visitRepository;
        this.authClient = authClient;
        this.mapper = mapper;
        this.assembler = assembler;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private record Actor(Long id, RoleCode role) {
    }

    /** Không @Transactional: lời gọi OpenFeign phải nằm ngoài transaction (quy chuẩn §9.1). */
    @Override
    public PetResponse create(CreatePetRequest request) {
        Actor actor = currentActor();
        Long ownerId = switch (actor.role()) {
            // Chủ nuôi: ownerId lấy từ token, không bao giờ tin vào body
            case PET_OWNER -> actor.id();
            case RECEPTIONIST -> {
                if (request.ownerId() == null) {
                    throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Lễ tân phải chọn chủ thú cưng (ownerId)");
                }
                assertActivePetOwner(request.ownerId());
                yield request.ownerId();
            }
            default -> throw new AppException(ErrorCode.ACCESS_DENIED);
        };

        return transactionTemplate.execute(tx -> {
            Species species = speciesRepository.findById(request.speciesId())
                    .orElseThrow(() -> new AppException(ErrorCode.SPECIES_NOT_FOUND));
            Pet pet = Pet.builder()
                    // Mã tạm duy nhất; đổi thành mã theo id ngay bên dưới (id chỉ có sau khi INSERT)
                    .petCode("TMP-" + UUID.randomUUID())
                    .ownerId(ownerId)
                    .species(species)
                    .name(request.name().trim())
                    .breed(blankToNull(request.breed()))
                    .gender(request.gender() == null ? PetGender.UNKNOWN : request.gender())
                    .dateOfBirth(request.dateOfBirth())
                    .color(blankToNull(request.color()))
                    .weight(request.weight())
                    .neutered(Boolean.TRUE.equals(request.neutered()))
                    .identifyingCharacteristics(blankToNull(request.identifyingCharacteristics()))
                    .notes(blankToNull(request.notes()))
                    .build();
            pet = petRepository.saveAndFlush(pet);
            pet.setPetCode(String.format("PET-%d-%05d", LocalDate.now(clock).getYear(), pet.getId()));
            return mapper.toResponse(pet);
        });
    }

    @Override
    @Transactional
    public PetResponse update(Long id, UpdatePetRequest request) {
        Actor actor = currentActor();
        if (actor.role() != RoleCode.PET_OWNER && actor.role() != RoleCode.RECEPTIONIST) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        Pet pet = findPet(id);
        assertOwnerAccess(actor, pet);

        if (request.name() != null) {
            pet.setName(request.name().trim());
        }
        if (request.breed() != null) {
            pet.setBreed(blankToNull(request.breed()));
        }
        if (request.gender() != null) {
            pet.setGender(request.gender());
        }
        if (request.dateOfBirth() != null) {
            pet.setDateOfBirth(request.dateOfBirth());
        }
        if (request.color() != null) {
            pet.setColor(blankToNull(request.color()));
        }
        if (request.weight() != null) {
            pet.setWeight(request.weight());
        }
        if (request.neutered() != null) {
            pet.setNeutered(request.neutered());
        }
        if (request.identifyingCharacteristics() != null) {
            pet.setIdentifyingCharacteristics(blankToNull(request.identifyingCharacteristics()));
        }
        if (request.notes() != null) {
            pet.setNotes(blankToNull(request.notes()));
        }
        return mapper.toResponse(petRepository.saveAndFlush(pet));
    }

    @Override
    @Transactional(readOnly = true)
    public PetResponse getById(Long id) {
        Pet pet = findPet(id);
        assertOwnerAccess(currentActor(), pet);
        return mapper.toResponse(pet);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PetResponse> search(Long ownerId, String keyword, int page, int size) {
        Actor actor = currentActor();
        Long effectiveOwner = actor.role() == RoleCode.PET_OWNER ? actor.id() : ownerId;

        Specification<Pet> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveOwner != null) {
                predicates.add(cb.equal(root.get("ownerId"), effectiveOwner));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("petCode")), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Pet> result = petRepository.findAll(spec, pageable);
        return PageResponse.of(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast(), mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitResponse> getVisits(Long petId) {
        Pet pet = findPet(petId);
        assertOwnerAccess(currentActor(), pet);
        return assembler.summaries(visitRepository.findByPetIdOrderByIdDesc(petId));
    }

    @Override
    @Transactional(readOnly = true)
    public PetSummaryResponse getSummary(Long id) {
        Pet pet = petRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PET_NOT_FOUND));
        return new PetSummaryResponse(pet.getId(), pet.getOwnerId(), pet.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PetSummaryResponse> getSummaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return petRepository.findAllById(ids).stream()
                .map(p -> new PetSummaryResponse(p.getId(), p.getOwnerId(), p.getName())).toList();
    }

    // ------------------------------------------------------------------ helper

    private Pet findPet(Long id) {
        return petRepository.findWithSpeciesById(id).orElseThrow(() -> new AppException(ErrorCode.PET_NOT_FOUND));
    }

    /** Chủ nuôi chỉ thao tác được trên thú cưng của mình; của người khác trả "không tìm thấy" để không lộ sự tồn tại. */
    private void assertOwnerAccess(Actor actor, Pet pet) {
        if (actor.role() == RoleCode.PET_OWNER && !pet.getOwnerId().equals(actor.id())) {
            throw new AppException(ErrorCode.PET_NOT_FOUND);
        }
    }

    private Actor currentActor() {
        return new Actor(SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED)));
    }

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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
