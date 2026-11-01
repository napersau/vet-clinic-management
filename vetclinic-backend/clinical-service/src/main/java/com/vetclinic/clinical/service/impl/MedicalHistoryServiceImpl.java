package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.client.BillingClient;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.response.MedicalHistoryEntry;
import com.vetclinic.clinical.dto.response.PrescriptionSummary;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.repository.PetRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.service.MedicalHistoryService;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.dto.PageResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Không @Transactional ở mức class: phần đọc CSDL chạy trong TransactionTemplate chỉ-đọc, còn các lời gọi OpenFeign
 * (tên bác sĩ từ auth, đơn thuốc từ billing) nằm NGOÀI transaction. Hai nguồn ngoài chỉ làm giàu: lỗi thì để null, không
 * làm hỏng bệnh án.
 */
@Slf4j
@Service
public class MedicalHistoryServiceImpl implements MedicalHistoryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PetRepository petRepository;
    private final PetVisitRepository visitRepository;
    private final VisitAssembler assembler;
    private final AuthClient authClient;
    private final BillingClient billingClient;
    private final TransactionTemplate readTx;

    public MedicalHistoryServiceImpl(PetRepository petRepository, PetVisitRepository visitRepository,
                                     VisitAssembler assembler, AuthClient authClient, BillingClient billingClient,
                                     PlatformTransactionManager transactionManager) {
        this.petRepository = petRepository;
        this.visitRepository = visitRepository;
        this.assembler = assembler;
        this.authClient = authClient;
        this.billingClient = billingClient;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    @Override
    public PageResponse<MedicalHistoryEntry> history(Long petId, int page, int size) {
        Long actorId = SecurityUtils.getCurrentUserId();
        RoleCode role = SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        boolean owner = role == RoleCode.PET_OWNER;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "checkedInAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<VisitResponse> visits = readTx.execute(status -> {
            Pet pet = petRepository.findWithSpeciesById(petId).orElseThrow(() -> new AppException(ErrorCode.PET_NOT_FOUND));
            if (owner && !pet.getOwnerId().equals(actorId)) {
                throw new AppException(ErrorCode.PET_NOT_FOUND);   // không lộ sự tồn tại của hồ sơ người khác
            }
            // Chủ nuôi chỉ thấy các đợt khám đã hoàn tất, không thấy ca đang khám dở hoặc đã hủy
            Page<PetVisit> found = owner
                    ? visitRepository.findByPetIdAndStatus(petId, VisitStatus.COMPLETED, pageable)
                    : visitRepository.findByPetId(petId, pageable);
            return found.map(assembler::detail);
        });

        List<Long> visitIds = visits.getContent().stream().map(VisitResponse::id).toList();
        Map<Long, PrescriptionSummary> prescriptions = prescriptionsOf(visitIds);
        Map<Long, String> doctorNames = doctorNamesOf(visits.getContent().stream().map(VisitResponse::doctorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        return PageResponse.of(visits.getContent(), visits.getNumber(), visits.getSize(), visits.getTotalElements(),
                visits.getTotalPages(), visits.isLast(), v -> new MedicalHistoryEntry(v,
                        v.doctorId() == null ? null : doctorNames.get(v.doctorId()), prescriptions.get(v.id())));
    }

    private Map<Long, PrescriptionSummary> prescriptionsOf(List<Long> visitIds) {
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<PrescriptionSummary>> response = billingClient.getPrescriptions(visitIds);
            List<PrescriptionSummary> list = response == null || response.getData() == null ? List.of() : response.getData();
            return list.stream().collect(Collectors.toMap(PrescriptionSummary::visitId, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Không lấy được đơn thuốc từ billing-pharmacy-service: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, String> doctorNamesOf(Set<Long> doctorIds) {
        if (doctorIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<UserSummaryResponse>> response = authClient.getUsers(List.copyOf(doctorIds));
            List<UserSummaryResponse> users = response == null || response.getData() == null ? List.of() : response.getData();
            return users.stream().collect(Collectors.toMap(UserSummaryResponse::id, UserSummaryResponse::fullName, (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Không lấy được tên bác sĩ từ auth-service: {}", e.getMessage());
            return Map.of();
        }
    }
}
