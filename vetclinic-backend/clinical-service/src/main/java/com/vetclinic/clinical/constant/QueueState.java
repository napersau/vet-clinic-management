package com.vetclinic.clinical.constant;

/** Vị trí của một chỉ định trong hàng đợi phòng Lab / CĐHA. Suy ra từ trạng thái và lịch sử, không lưu riêng. */
public enum QueueState {
    WAITING,
    POSTPONED,
    NEXT_IN_LINE,
    IN_PROGRESS
}
