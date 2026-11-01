package com.vetclinic.clinical.service;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.PetVisitStatusHistory;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Các thao tác dùng chung khi đổi trạng thái đợt khám. Luôn gọi bên trong một transaction.
 * Mỗi lần đổi trạng thái đều ghi bảng pet_visit_status_histories trong cùng transaction (quy chuẩn §6).
 * Phần chỉ định dịch vụ (recordDetail) và chuyển RESULTS_READY khi đủ kết quả cận lâm sàng thêm ở NV2 / NV3.
 */
@Component
@RequiredArgsConstructor
public class VisitSupport {

    private final PetVisitRepository visitRepository;
    private final PetVisitStatusHistoryRepository historyRepository;

    /** Khóa dòng đợt khám (SELECT ... FOR UPDATE). Bắt buộc dùng trước mọi thao tác ghi. */
    public PetVisit lock(Long visitId) {
        return visitRepository.findByIdForUpdate(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
    }

    public void assertStatus(PetVisit visit, VisitStatus expected, String message) {
        if (visit.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION, message);
        }
    }

    /** Chỉ Bác sĩ được Lễ tân phân công mới thao tác chuyên môn trên ca này. */
    public void assertAssignedTo(PetVisit visit, Long doctorId) {
        if (visit.getDoctorId() == null || !visit.getDoctorId().equals(doctorId)) {
            throw new AppException(ErrorCode.VISIT_NOT_ASSIGNED_TO_YOU);
        }
    }

    public void transition(PetVisit visit, VisitStatus to, Long actorId, String note) {
        VisitStatus from = visit.getStatus();
        if (!from.canTransitionTo(to)) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Không thể chuyển đợt khám từ " + from + " sang " + to);
        }
        visit.setStatus(to);
        visitRepository.save(visit);
        record(visit.getId(), from, to, actorId, note);
    }

    public void record(Long visitId, VisitStatus from, VisitStatus to, Long actorId, String note) {
        historyRepository.save(PetVisitStatusHistory.builder()
                .visitId(visitId)
                .fromStatus(from)
                .toStatus(to)
                .changedByUserId(actorId)
                .notes(note)
                .build());
    }
}
