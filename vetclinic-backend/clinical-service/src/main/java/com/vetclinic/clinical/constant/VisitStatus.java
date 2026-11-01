package com.vetclinic.clinical.constant;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Khớp ENUM pet_visits.status. Các cạnh hợp lệ lấy từ THIET_KE_SO_DO_TRANG_THAI_STATE_MACHINE.md (SM-01).
 */
public enum VisitStatus {
    WAITING,
    IN_PROGRESS,
    WAITING_RESULTS,
    RESULTS_READY,
    WAITING_PAYMENT,
    COMPLETED,
    CANCELLED;

    private static final Map<VisitStatus, Set<VisitStatus>> TRANSITIONS = new EnumMap<>(VisitStatus.class);

    static {
        TRANSITIONS.put(WAITING, EnumSet.of(IN_PROGRESS, CANCELLED));
        // GĐ1 -> chỉ định CLS | chốt ngay (khám nhẹ, tiêm phòng) | hủy / chuyển viện. GĐ2 chốt -> WAITING_PAYMENT
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(WAITING_RESULTS, WAITING_PAYMENT, CANCELLED));
        TRANSITIONS.put(WAITING_RESULTS, EnumSet.of(RESULTS_READY, CANCELLED));
        // Gọi lại vào phòng (GĐ2) | khách bỏ về hẳn (EX-11) đóng ca và ghi nợ
        TRANSITIONS.put(RESULTS_READY, EnumSet.of(IN_PROGRESS, COMPLETED));
        // Thu tiền xong | Admin mở lại đợt khám để điều chỉnh chuyên môn (T12, hóa đơn PENDING bị hủy, chưa thu tiền)
        TRANSITIONS.put(WAITING_PAYMENT, EnumSet.of(COMPLETED, IN_PROGRESS));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(VisitStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(VisitStatus.class));
    }

    /** Các trạng thái mà đợt khám còn đang diễn ra (một thú cưng chỉ được có một đợt như vậy). */
    public static final Set<VisitStatus> ACTIVE =
            EnumSet.of(WAITING, IN_PROGRESS, WAITING_RESULTS, RESULTS_READY, WAITING_PAYMENT);

    public boolean canTransitionTo(VisitStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
