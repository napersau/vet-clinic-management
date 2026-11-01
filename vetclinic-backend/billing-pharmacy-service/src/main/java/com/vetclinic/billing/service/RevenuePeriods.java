package com.vetclinic.billing.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Chia khoảng ngày thành các kỳ báo cáo (ngày / tuần ISO bắt đầu thứ Hai / tháng / quý) theo lịch của phòng khám. */
public final class RevenuePeriods {

    public enum Granularity {
        DAY, WEEK, MONTH, QUARTER
    }

    private RevenuePeriods() {
    }

    /** Ngày đầu của kỳ chứa date. */
    public static LocalDate start(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> date.withDayOfMonth(1).withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1);
        };
    }

    public static LocalDate next(LocalDate periodStart, Granularity granularity) {
        return switch (granularity) {
            case DAY -> periodStart.plusDays(1);
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
            case QUARTER -> periodStart.plusMonths(3);
        };
    }

    public static String label(LocalDate periodStart, Granularity granularity) {
        return switch (granularity) {
            case DAY -> periodStart.toString();
            case WEEK -> periodStart + " (tuần)";
            case MONTH -> String.format("%d-%02d", periodStart.getYear(), periodStart.getMonthValue());
            case QUARTER -> periodStart.getYear() + "-Q" + ((periodStart.getMonthValue() - 1) / 3 + 1);
        };
    }

    /** Mọi kỳ từ kỳ chứa from đến kỳ chứa to, kể cả kỳ không có doanh thu (để biểu đồ liên tục). */
    public static List<LocalDate> between(LocalDate from, LocalDate to, Granularity granularity) {
        List<LocalDate> periods = new ArrayList<>();
        LocalDate last = start(to, granularity);
        for (LocalDate p = start(from, granularity); !p.isAfter(last); p = next(p, granularity)) {
            periods.add(p);
        }
        return periods;
    }
}
