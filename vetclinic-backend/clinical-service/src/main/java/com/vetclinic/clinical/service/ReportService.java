package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.response.OperationsReportResponse;

import java.time.LocalDate;

public interface ReportService {

    /**
     * FR-70 / UC-08: chỉ số vận hành theo ngày check-in (giờ phòng khám). Mặc định 30 ngày gần nhất, tối đa 366 ngày.
     */
    OperationsReportResponse operations(LocalDate fromDate, LocalDate toDate);
}
