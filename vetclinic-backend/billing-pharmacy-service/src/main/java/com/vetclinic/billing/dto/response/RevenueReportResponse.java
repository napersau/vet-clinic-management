package com.vetclinic.billing.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo doanh thu (FR-69). Doanh thu chỉ tính hóa đơn PAID theo ngày thu tiền (giờ phòng khám). Phân rã hai nguồn:
 * dịch vụ kỹ thuật và thuốc (trước VAT và chiết khấu), kèm VAT và chiết khấu; finalAmount = dịch vụ + thuốc + VAT - chiết khấu.
 * byStatus thống kê số hóa đơn và tiền theo trạng thái cho các hóa đơn LẬP trong kỳ (PENDING = công nợ chưa thu).
 */
public record RevenueReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String groupBy,
        Totals totals,
        List<Period> periods,
        Map<String, StatusTotal> byStatus
) {

    public record Totals(
            long invoiceCount,
            BigDecimal serviceAmount,
            BigDecimal medicineAmount,
            BigDecimal vatAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount
    ) {
    }

    public record Period(
            String label,
            LocalDate periodStart,
            long invoiceCount,
            BigDecimal serviceAmount,
            BigDecimal medicineAmount,
            BigDecimal vatAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount
    ) {
    }

    public record StatusTotal(long invoiceCount, BigDecimal finalAmount) {
    }
}
