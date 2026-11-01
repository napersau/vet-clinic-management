package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.response.ServiceDetailResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Dựng VisitResponse. Phải gọi bên trong transaction (hoặc với entity đã nạp kèm pet và loài) vì open-in-view đã tắt.
 * Kết quả xét nghiệm / ảnh CĐHA (labResults, imagingResults) của detail() thêm ở NV3.
 */
@Component
@RequiredArgsConstructor
public class VisitAssembler {

    private final VisitServiceDetailRepository detailRepository;

    /** Dùng cho API danh sách: không kèm dịch vụ. */
    public VisitResponse summary(PetVisit visit) {
        return build(visit, null);
    }

    public List<VisitResponse> summaries(Collection<PetVisit> visits) {
        return visits.stream().map(this::summary).toList();
    }

    /** Chi tiết đầy đủ: kèm các dịch vụ đã chỉ định (đơn giá snapshot, trạng thái). */
    public VisitResponse detail(PetVisit visit) {
        return build(visit, detailRepository.findByVisitIdOrderByIdAsc(visit.getId()).stream()
                .map(this::toServiceDetail).toList());
    }

    public ServiceDetailResponse toServiceDetail(VisitServiceDetail d) {
        return new ServiceDetailResponse(d.getId(), d.getVisitId(), d.getService().getId(), d.getService().getCode(),
                d.getService().getName(), d.getService().getCategory(), d.getUnitPrice(), d.getQuantity(),
                d.getSubtotal(), d.getStatus(), d.getNotes(), d.getCreatedAt());
    }

    private VisitResponse build(PetVisit v, List<ServiceDetailResponse> services) {
        Pet pet = v.getPet();
        boolean locked = switch (v.getStatus()) {
            case WAITING_PAYMENT, COMPLETED, CANCELLED -> true;
            default -> false;
        };
        return new VisitResponse(v.getId(), v.getVisitCode(), pet.getId(), pet.getName(), pet.getPetCode(),
                pet.getSpecies().getName(), pet.getOwnerId(), v.getAppointmentId(), v.getDoctorId(),
                v.getReceptionistId(), v.getAssignedRoom(), v.isEmergency(), v.getStatus(), locked,
                v.getCallbackPostponedAt() != null, v.getWeightAtVisit(), v.getTemperature(), v.getInitialSymptoms(),
                v.getClinicalNotes(), v.getDiagnosis(), v.getTreatmentPlan(), v.getDoctorAdvice(), v.getCancelReason(),
                v.getCheckedInAt(), v.getScheduledAt(), v.getStartedAt(), v.getClosedAt(), v.getCompletedAt(), services);
    }
}
