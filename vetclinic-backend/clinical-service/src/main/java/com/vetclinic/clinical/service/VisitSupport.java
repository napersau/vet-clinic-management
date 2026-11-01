package com.vetclinic.clinical.service;

import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.PetVisitStatusHistory;
import com.vetclinic.clinical.entity.VisitServiceDetailHistory;
import com.vetclinic.clinical.repository.VisitServiceDetailHistoryRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import com.vetclinic.common.event.DoctorPriorityCallbackEvent;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Các thao tác dùng chung khi đổi trạng thái đợt khám. Luôn gọi bên trong một transaction.
 * Mỗi lần đổi trạng thái đều ghi bảng pet_visit_status_histories trong cùng transaction (quy chuẩn §6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitSupport {

    private final PetVisitRepository visitRepository;
    private final PetVisitStatusHistoryRepository historyRepository;
    private final VisitServiceDetailRepository detailRepository;
    private final VisitServiceDetailHistoryRepository detailHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

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

    /** Audit trail tiến độ một chỉ định. action: ORDERED, START_PROCESSING, COMPLETED, CANCELLED, POSTPONED, RECALL. */
    public void recordDetail(Long detailId, ServiceDetailStatus from, ServiceDetailStatus to, String action,
                             Long actorId, String reason) {
        detailHistoryRepository.save(VisitServiceDetailHistory.builder()
                .visitServiceDetailId(detailId)
                .fromStatus(from)
                .toStatus(to)
                .action(action)
                .changedByUserId(actorId)
                .reason(reason)
                .build());
    }

    /**
     * FR-44: mỗi khi một dịch vụ cận lâm sàng COMPLETED / CANCELLED, nếu không còn dịch vụ Lab / CĐHA nào
     * PENDING hoặc IN_PROGRESS thì ca WAITING_RESULTS chuyển sang RESULTS_READY và báo cho Bác sĩ (Khóa ưu tiên).
     * Phải gọi SAU KHI đã khóa dòng đợt khám và trong transaction READ_COMMITTED, để thấy kết quả mới nhất của
     * kỹ thuật viên phòng còn lại vừa commit.
     */
    public void evaluateParaclinicalCompletion(PetVisit visit, Long actorId) {
        if (visit.getStatus() != VisitStatus.WAITING_RESULTS) {
            return;
        }
        long open = detailRepository.countByVisitAndCategoriesAndStatuses(
                visit.getId(), ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN);
        if (open > 0) {
            return;
        }
        visit.setCallbackPostponedAt(null);
        transition(visit, VisitStatus.RESULTS_READY, actorId,
                "Đã có đủ kết quả cận lâm sàng, chuyển vào hàng đợi tái khám ưu tiên của Bác sĩ");
        eventPublisher.publishEvent(new DoctorPriorityCallbackEvent(UUID.randomUUID(), clock.instant(), actorId,
                visit.getId(), visit.getVisitCode(), visit.getDoctorId(), visit.getPet().getId()));
        log.info("Đợt khám {} đã có đủ kết quả cận lâm sàng (RESULTS_READY)", visit.getVisitCode());
    }
}
