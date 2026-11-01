package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.response.OperationsReportResponse;
import com.vetclinic.clinical.service.ReportService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/clinical/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** FR-70: tổng số lượt khám, cấp cứu, ca thường vs ca có cận lâm sàng, hoàn tất, hủy và số ca của từng Bác sĩ. */
    @GetMapping("/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OperationsReportResponse>> operations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(reportService.operations(fromDate, toDate)));
    }
}
