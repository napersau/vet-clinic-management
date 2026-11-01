package com.vetclinic.clinical.constant;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.vetclinic.clinical.constant.VisitStatus.CANCELLED;
import static com.vetclinic.clinical.constant.VisitStatus.COMPLETED;
import static com.vetclinic.clinical.constant.VisitStatus.IN_PROGRESS;
import static com.vetclinic.clinical.constant.VisitStatus.RESULTS_READY;
import static com.vetclinic.clinical.constant.VisitStatus.WAITING;
import static com.vetclinic.clinical.constant.VisitStatus.WAITING_PAYMENT;
import static com.vetclinic.clinical.constant.VisitStatus.WAITING_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;

/** Ma trận chuyển trạng thái phải khớp bảng T01..T12 trong THIET_KE_SO_DO_TRANG_THAI_STATE_MACHINE.md. */
class VisitStatusTest {

    @Test
    void allowedTransitionsMatchTheStateMachineDocument() {
        assertThat(targets(WAITING)).containsExactlyInAnyOrder(IN_PROGRESS, CANCELLED);
        assertThat(targets(IN_PROGRESS)).containsExactlyInAnyOrder(WAITING_RESULTS, WAITING_PAYMENT, CANCELLED);
        assertThat(targets(WAITING_RESULTS)).containsExactlyInAnyOrder(RESULTS_READY, CANCELLED);
        assertThat(targets(RESULTS_READY)).containsExactlyInAnyOrder(IN_PROGRESS, COMPLETED);
        assertThat(targets(WAITING_PAYMENT)).containsExactlyInAnyOrder(COMPLETED, IN_PROGRESS);   // T10 và T12 (mở lại)
    }

    @Test
    void finishedVisitsNeverMoveAgain() {
        assertThat(targets(COMPLETED)).isEmpty();
        assertThat(targets(CANCELLED)).isEmpty();
    }

    @Test
    void criticalShortcutsAreForbidden() {
        // Không được nhảy cóc: thu tiền khi chưa chốt, hoàn tất khi chưa thanh toán, hồi sinh ca đã hủy
        assertThat(WAITING.canTransitionTo(WAITING_PAYMENT)).isFalse();
        assertThat(WAITING.canTransitionTo(COMPLETED)).isFalse();
        assertThat(WAITING_RESULTS.canTransitionTo(WAITING_PAYMENT)).isFalse();
        assertThat(RESULTS_READY.canTransitionTo(WAITING_PAYMENT)).isFalse();
        assertThat(WAITING_PAYMENT.canTransitionTo(WAITING)).isFalse();
        assertThat(WAITING_PAYMENT.canTransitionTo(CANCELLED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(WAITING)).isFalse();
    }

    @Test
    void activeStatesExcludeFinishedOnes() {
        assertThat(VisitStatus.ACTIVE).containsExactlyInAnyOrder(WAITING, IN_PROGRESS, WAITING_RESULTS, RESULTS_READY, WAITING_PAYMENT);
    }

    private static Set<VisitStatus> targets(VisitStatus from) {
        Set<VisitStatus> result = EnumSet.noneOf(VisitStatus.class);
        for (VisitStatus to : VisitStatus.values()) {
            if (from.canTransitionTo(to)) {
                result.add(to);
            }
        }
        return result;
    }
}
