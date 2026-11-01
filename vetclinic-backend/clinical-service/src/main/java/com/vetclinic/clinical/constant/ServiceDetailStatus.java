package com.vetclinic.clinical.constant;

import java.util.EnumSet;
import java.util.Set;

/** Khớp ENUM visit_service_details.status. */
public enum ServiceDetailStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /** Còn dở dang: chặn ca khám chuyển sang RESULTS_READY. */
    public static final Set<ServiceDetailStatus> OPEN = EnumSet.of(PENDING, IN_PROGRESS);
}
