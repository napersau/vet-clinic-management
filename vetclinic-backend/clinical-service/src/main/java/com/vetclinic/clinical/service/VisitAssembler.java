package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Dựng VisitResponse. Phải gọi bên trong transaction (hoặc với entity đã nạp kèm pet và loài) vì open-in-view đã tắt.
 * detail() sẽ kèm dịch vụ đã chỉ định (NV2) và kết quả xét nghiệm / ảnh CĐHA (NV3); hiện chưa có các phần đó.
 */
@Component
public class VisitAssembler {

    /** Dùng cho API danh sách. */
    public VisitResponse summary(PetVisit visit) {
        return build(visit);
    }

    public List<VisitResponse> summaries(Collection<PetVisit> visits) {
        return visits.stream().map(this::summary).toList();
    }

    /** Chi tiết một đợt khám. */
    public VisitResponse detail(PetVisit visit) {
        return build(visit);
    }

    private VisitResponse build(PetVisit v) {
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
                v.getCheckedInAt(), v.getScheduledAt(), v.getStartedAt(), v.getClosedAt(), v.getCompletedAt());
    }
}
