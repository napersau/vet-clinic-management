package com.vetclinic.clinical.constant;

/** Trạng thái phục vụ của Bác sĩ. KHÔNG lưu trong DB, tính động từ các ca khám (state machine SM-02). */
public enum DoctorStatus {
    AVAILABLE,
    AVAILABLE_PRIORITY_CALLBACK,
    BUSY
}
