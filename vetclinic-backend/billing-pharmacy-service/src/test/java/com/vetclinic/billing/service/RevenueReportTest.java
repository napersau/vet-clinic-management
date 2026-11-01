package com.vetclinic.billing.service;

import com.vetclinic.billing.config.BillingProperties;
import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.dto.response.RevenueReportResponse;
import com.vetclinic.billing.repository.InvoiceRepository;
import com.vetclinic.billing.repository.RevenueRow;
import com.vetclinic.billing.repository.StatusRow;
import com.vetclinic.billing.service.RevenuePeriods.Granularity;
import com.vetclinic.billing.service.impl.ReportServiceImpl;
import com.vetclinic.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.vetclinic.billing.testsupport.TestData.CLOCK;
import static com.vetclinic.billing.testsupport.TestData.assertError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        BillingProperties properties = new BillingProperties(
                new BillingProperties.Vat(BigDecimal.ZERO, BigDecimal.ZERO), "Asia/Ho_Chi_Minh");
        service = new ReportServiceImpl(invoiceRepository, properties, CLOCK);
    }

    private static RevenueRow paid(String instant, String service, String medicine, String vat, String discount) {
        BigDecimal s = new BigDecimal(service), m = new BigDecimal(medicine), v = new BigDecimal(vat), d = new BigDecimal(discount);
        return new RevenueRow(Instant.parse(instant), s, m, v, d, s.add(m).add(v).subtract(d));
    }

    // ================================================================== kỳ báo cáo

    @Test
    void periods_startOnTheCalendarBoundaryOfEachGranularity() {
        LocalDate wednesday = LocalDate.of(2026, 11, 18);
        assertThat(RevenuePeriods.start(wednesday, Granularity.DAY)).isEqualTo(wednesday);
        assertThat(RevenuePeriods.start(wednesday, Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 16));   // thứ Hai
        assertThat(RevenuePeriods.start(wednesday, Granularity.MONTH)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(RevenuePeriods.start(wednesday, Granularity.QUARTER)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(RevenuePeriods.start(LocalDate.of(2026, 2, 28), Granularity.QUARTER)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(RevenuePeriods.label(LocalDate.of(2026, 10, 1), Granularity.QUARTER)).isEqualTo("2026-Q4");
        assertThat(RevenuePeriods.label(LocalDate.of(2026, 11, 1), Granularity.MONTH)).isEqualTo("2026-11");
    }

    @Test
    void periods_between_includesEmptyPeriodsSoChartsAreContinuous() {
        assertThat(RevenuePeriods.between(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2), Granularity.DAY)).hasSize(4);
        assertThat(RevenuePeriods.between(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 12, 3), Granularity.MONTH))
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
        // 28/10 (thứ Tư) -> tuần bắt đầu 26/10; 03/11 (thứ Ba) -> tuần bắt đầu 02/11
        assertThat(RevenuePeriods.between(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3), Granularity.WEEK))
                .containsExactly(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2));
    }

    // ================================================================== báo cáo doanh thu

    @Test
    void revenue_groupsPaidInvoicesByClinicDayAndSplitsServiceFromMedicine() {
        // 17:30 UTC ngày 28/10 = 00:30 ngày 29/10 giờ Việt Nam -> thuộc ngày 29/10
        when(invoiceRepository.findPaidRevenueRows(any(), any())).thenReturn(List.of(
                paid("2026-10-28T03:00:00Z", "250000", "45000", "0", "0"),
                paid("2026-10-28T17:30:00Z", "100000", "0", "10000", "20000"),
                paid("2026-10-29T03:00:00Z", "150000", "90000", "0", "0")));
        when(invoiceRepository.summarizeByStatus(any(), any())).thenReturn(List.of(
                new StatusRow(PaymentStatus.PAID, 3L, new BigDecimal("625000")),
                new StatusRow(PaymentStatus.PENDING, 2L, new BigDecimal("300000"))));

        RevenueReportResponse report = service.revenue(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30), Granularity.DAY);

        assertThat(report.periods()).extracting(RevenueReportResponse.Period::label).containsExactly("2026-10-28", "2026-10-29", "2026-10-30");
        assertThat(report.periods().get(0).finalAmount()).isEqualByComparingTo("295000");
        assertThat(report.periods().get(1).invoiceCount()).isEqualTo(2);
        assertThat(report.periods().get(1).serviceAmount()).isEqualByComparingTo("250000");
        assertThat(report.periods().get(1).medicineAmount()).isEqualByComparingTo("90000");
        assertThat(report.periods().get(2).invoiceCount()).isZero();      // ngày không có doanh thu vẫn có dòng
        assertThat(report.totals().invoiceCount()).isEqualTo(3);
        assertThat(report.totals().serviceAmount()).isEqualByComparingTo("500000");
        assertThat(report.totals().medicineAmount()).isEqualByComparingTo("135000");
        assertThat(report.totals().vatAmount()).isEqualByComparingTo("10000");
        assertThat(report.totals().discountAmount()).isEqualByComparingTo("20000");
        assertThat(report.totals().finalAmount()).isEqualByComparingTo("625000");   // 500000 + 135000 + 10000 - 20000
        assertThat(report.byStatus().get("PENDING").finalAmount()).isEqualByComparingTo("300000");   // công nợ
        assertThat(report.byStatus().get("CANCELLED").invoiceCount()).isZero();   // đủ ba trạng thái
    }

    @Test
    void revenue_queriesTheClinicDayBoundariesNotUtc() {
        when(invoiceRepository.findPaidRevenueRows(any(), any())).thenReturn(List.of());
        when(invoiceRepository.summarizeByStatus(any(), any())).thenReturn(List.of());

        service.revenue(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28), Granularity.DAY);

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(invoiceRepository).findPaidRevenueRows(start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-10-27T17:00:00Z"));   // 00:00 ngày 28 giờ VN
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-10-28T17:00:00Z"));     // loại trừ 00:00 ngày 29
    }

    @Test
    void revenue_groupsByMonthAndQuarter() {
        when(invoiceRepository.findPaidRevenueRows(any(), any())).thenReturn(List.of(
                paid("2026-10-15T03:00:00Z", "100000", "0", "0", "0"),
                paid("2026-11-15T03:00:00Z", "200000", "0", "0", "0")));
        when(invoiceRepository.summarizeByStatus(any(), any())).thenReturn(List.of());

        RevenueReportResponse months = service.revenue(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), Granularity.MONTH);
        RevenueReportResponse quarter = service.revenue(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), Granularity.QUARTER);

        assertThat(months.periods()).extracting(RevenueReportResponse.Period::label).containsExactly("2026-10", "2026-11");
        assertThat(quarter.periods()).hasSize(1);
        assertThat(quarter.periods().get(0).label()).isEqualTo("2026-Q4");
        assertThat(quarter.periods().get(0).finalAmount()).isEqualByComparingTo("300000");
    }

    @Test
    void revenue_defaultsToTheLast30DaysUpToToday() {
        when(invoiceRepository.findPaidRevenueRows(any(), any())).thenReturn(List.of());
        when(invoiceRepository.summarizeByStatus(any(), any())).thenReturn(List.of());

        RevenueReportResponse report = service.revenue(null, null, null);

        assertThat(report.toDate()).isEqualTo(LocalDate.of(2026, 10, 30));     // CLOCK = 30/10/2026 giờ VN
        assertThat(report.fromDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(report.groupBy()).isEqualTo("DAY");
        assertThat(report.periods()).hasSize(30);
    }

    @Test
    void revenue_refusesInvertedOrTooLongRanges() {
        assertError(ErrorCode.INVALID_INPUT_DATA,
                () -> service.revenue(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), Granularity.DAY));
        assertError(ErrorCode.INVALID_INPUT_DATA,
                () -> service.revenue(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 30), Granularity.DAY));
    }
}
