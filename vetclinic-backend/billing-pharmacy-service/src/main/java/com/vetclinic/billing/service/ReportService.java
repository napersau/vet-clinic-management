package com.vetclinic.billing.service;

import com.vetclinic.billing.dto.response.RevenueReportResponse;

import java.time.LocalDate;

public interface ReportService {

    /**
     * FR-69 / UC-08: doanh thu theo ngày / tuần / tháng / quý trong khoảng tùy chọn (mặc định 30 ngày gần nhất, tối đa
     * 366 ngày). Ngày tính theo giờ phòng khám.
     */
    RevenueReportResponse revenue(LocalDate fromDate, LocalDate toDate, RevenuePeriods.Granularity groupBy);
}
