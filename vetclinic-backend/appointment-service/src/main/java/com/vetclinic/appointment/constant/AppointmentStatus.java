package com.vetclinic.appointment.constant;

import java.util.EnumSet;
import java.util.Set;

/**
 * Khớp 100% với ENUM appointments.status trong pet_clinic_appointment_db.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CANCELLED,
    NO_SHOW;

    /** Các trạng thái đang chiếm một suất sức chứa của khung giờ (NGHIEP_VU §5.3). */
    public static final Set<AppointmentStatus> OCCUPYING = EnumSet.of(PENDING, CONFIRMED, CHECKED_IN);

    /** Các trạng thái mà chủ nuôi / lễ tân còn hủy hoặc đổi lịch được. */
    public static final Set<AppointmentStatus> MODIFIABLE = EnumSet.of(PENDING, CONFIRMED);
}
