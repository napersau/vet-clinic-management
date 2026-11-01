package com.vetclinic.billing.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Một hóa đơn đã thanh toán, chỉ các cột cần cho báo cáo doanh thu (không nạp entity và các dòng chi tiết). */
public record RevenueRow(
        Instant paidAt,
        BigDecimal serviceAmount,
        BigDecimal medicineAmount,
        BigDecimal vatAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount
) {
}
