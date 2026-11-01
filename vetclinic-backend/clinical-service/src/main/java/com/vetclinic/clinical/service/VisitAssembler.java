package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.response.AttachmentResponse;
import com.vetclinic.clinical.dto.response.ImagingResultResponse;
import com.vetclinic.clinical.dto.response.LabResultResponse;
import com.vetclinic.clinical.dto.response.ServiceDetailResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.entity.ImagingAttachment;
import com.vetclinic.clinical.entity.ImagingResult;
import com.vetclinic.clinical.entity.LabTestResult;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import com.vetclinic.clinical.repository.ImagingAttachmentRepository;
import com.vetclinic.clinical.repository.ImagingResultRepository;
import com.vetclinic.clinical.repository.LabTestResultRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dựng VisitResponse. Phải gọi bên trong transaction (hoặc với entity đã nạp kèm pet và loài) vì open-in-view đã tắt.
 */
@Component
@RequiredArgsConstructor
public class VisitAssembler {

    private final VisitServiceDetailRepository detailRepository;
    private final LabTestResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingAttachmentRepository attachmentRepository;

    /** Dùng cho API danh sách: không kèm dịch vụ và kết quả. */
    public VisitResponse summary(PetVisit visit) {
        return build(visit, null, null, null);
    }

    public List<VisitResponse> summaries(Collection<PetVisit> visits) {
        return visits.stream().map(this::summary).toList();
    }

    /** Chi tiết đầy đủ: dịch vụ đã chỉ định, kết quả xét nghiệm (có đánh giá LOW / NORMAL / HIGH) và ảnh CĐHA. */
    public VisitResponse detail(PetVisit visit) {
        List<VisitServiceDetail> details = detailRepository.findByVisitIdOrderByIdAsc(visit.getId());
        List<Long> detailIds = details.stream().map(VisitServiceDetail::getId).toList();

        List<LabResultResponse> labs = detailIds.isEmpty() ? List.of()
                : labResultRepository.findByVisitServiceDetailIdInOrderByIdAsc(detailIds).stream().map(this::toLab).toList();

        List<ImagingResult> imagingResults = detailIds.isEmpty() ? List.of()
                : imagingResultRepository.findByVisitServiceDetailIdIn(detailIds);
        Map<Long, List<ImagingAttachment>> attachments = imagingResults.isEmpty() ? Map.of()
                : attachmentRepository.findByImagingResultIdIn(imagingResults.stream().map(ImagingResult::getId).toList())
                .stream().collect(Collectors.groupingBy(ImagingAttachment::getImagingResultId));

        return build(visit,
                details.stream().map(this::toServiceDetail).toList(),
                labs,
                imagingResults.stream().map(r -> toImaging(r, attachments.getOrDefault(r.getId(), List.of()))).toList());
    }

    public ServiceDetailResponse toServiceDetail(VisitServiceDetail d) {
        return new ServiceDetailResponse(d.getId(), d.getVisitId(), d.getService().getId(), d.getService().getCode(),
                d.getService().getName(), d.getService().getCategory(), d.getUnitPrice(), d.getQuantity(),
                d.getSubtotal(), d.getStatus(), d.getNotes(), d.getCreatedAt());
    }

    private LabResultResponse toLab(LabTestResult r) {
        return new LabResultResponse(r.getId(), r.getVisitServiceDetailId(), r.getLabTestItem().getId(),
                r.getLabTestItem().getCode(), r.getLabTestItem().getName(), r.getLabTestItem().getUnit(),
                r.getResultValue(), r.getMinValueSnapshot(), r.getMaxValueSnapshot(), r.getEvaluation(),
                r.getPerformedBy(), r.getNotes(), r.getCreatedAt());
    }

    private ImagingResultResponse toImaging(ImagingResult r, List<ImagingAttachment> files) {
        List<AttachmentResponse> attachments = files.stream().map(a -> new AttachmentResponse(a.getId(), a.getFileName(),
                a.getFileSize(), a.getContentType(), "/api/v1/clinical/imaging-attachments/" + a.getId() + "/file")).toList();
        return new ImagingResultResponse(r.getId(), r.getVisitServiceDetailId(), r.getDescription(), r.getConclusion(),
                r.getPerformedBy(), r.getPerformedAt(), attachments);
    }

    private VisitResponse build(PetVisit v, List<ServiceDetailResponse> services, List<LabResultResponse> labs,
                                List<ImagingResultResponse> imaging) {
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
                v.getCheckedInAt(), v.getScheduledAt(), v.getStartedAt(), v.getClosedAt(), v.getCompletedAt(), services, labs, imaging);
    }
}
