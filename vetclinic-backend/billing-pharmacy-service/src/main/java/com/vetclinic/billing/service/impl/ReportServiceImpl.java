package com.vetclinic.billing.service.impl;

import com.vetclinic.billing.config.BillingProperties;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.dto.response.RevenueReportResponse;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.RevenueRow;
import com.vetclinic.billing.repository.StatusRow;
import com.vetclinic.billing.service.ReportService;
import com.vetclinic.billing.service.RevenuePeriods;
import com.vetclinic.billing.service.RevenuePeriods.Granularity;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_DAYS = 30;
    private static final long MAX_DAYS = 366;

    private final InvoiceRepository invoiceRepository;
    private final BillingProperties properties;
    private final Clock clock;

    /** Cộng dồn tiền của một nhóm hóa đơn. */
    private static final class Sum {
        long count;
        BigDecimal service = BigDecimal.ZERO;
        BigDecimal medicine = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        void add(RevenueRow row) {
            count++;
            service = service.add(row.serviceAmount());
            medicine = medicine.add(row.medicineAmount());
            vat = vat.add(row.vatAmount());
            discount = discount.add(row.discountAmount());
            total = total.add(row.finalAmount());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueReportResponse revenue(LocalDate fromDate, LocalDate toDate, Granularity groupBy) {
        ZoneId zone = properties.zoneId();
        LocalDate to = toDate != null ? toDate : LocalDate.now(clock);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Khoảng báo cáo tối đa " + MAX_DAYS + " ngày");
        }
        Granularity granularity = groupBy == null ? Granularity.DAY : groupBy;
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        Map<LocalDate, Sum> buckets = new TreeMap<>();
        RevenuePeriods.between(from, to, granularity).forEach(p -> buckets.put(p, new Sum()));
        Sum total = new Sum();
        for (RevenueRow row : invoiceRepository.findPaidRevenueRows(start, end)) {
            LocalDate paidDay = row.paidAt().atZone(zone).toLocalDate();
            buckets.computeIfAbsent(RevenuePeriods.start(paidDay, granularity), k -> new Sum()).add(row);
            total.add(row);
        }

        List<RevenueReportResponse.Period> periods = buckets.entrySet().stream().map(e -> new RevenueReportResponse.Period(
                RevenuePeriods.label(e.getKey(), granularity), e.getKey(), e.getValue().count, e.getValue().service,
                e.getValue().medicine, e.getValue().vat, e.getValue().discount, e.getValue().total)).toList();

        Map<String, RevenueReportResponse.StatusTotal> byStatus = new LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            byStatus.put(status.name(), new RevenueReportResponse.StatusTotal(0, BigDecimal.ZERO));
        }
        for (StatusRow row : invoiceRepository.summarizeByStatus(start, end)) {
            byStatus.put(row.status().name(), new RevenueReportResponse.StatusTotal(row.invoiceCount(), row.finalAmount()));
        }

        return new RevenueReportResponse(from, to, granularity.name(),
                new RevenueReportResponse.Totals(total.count, total.service, total.medicine, total.vat, total.discount,
                        total.total), periods, byStatus);
    }
}
