package com.vetclinic.billing.service;

/** Tên khóa MySQL (NamedLockService) dùng chung của billing-pharmacy-service. */
public final class BillingLocks {

    private BillingLocks() {
    }

    /**
     * Một khóa cho mọi thao tác "kiểm tra rồi ghi" liên quan tới đơn thuốc và hóa đơn của cùng một đợt khám: lưu / hủy đơn
     * thuốc và lập hóa đơn từ sự kiện chốt ca. Nhờ vậy đơn không thể lọt vào sau khi hóa đơn đã được lập (và ngược lại).
     */
    public static String visit(Long visitId) {
        return "vetclinic:visit-billing:" + visitId;
    }
}
