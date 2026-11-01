package com.vetclinic.billing.constant;

/** Trạng thái đợt khám bên clinical-service (billing chỉ đọc). Tên khớp enum pet_visits.status. */
public enum VisitStatus {
    WAITING,
    IN_PROGRESS,
    WAITING_RESULTS,
    RESULTS_READY,
    WAITING_PAYMENT,
    COMPLETED,
    CANCELLED
}
