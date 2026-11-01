package com.vetclinic.billing.client;

import com.vetclinic.billing.dto.response.BillableServiceResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Endpoint nội bộ của clinical-service (không cần header người dùng, Gateway không định tuyến ra ngoài). */
@FeignClient(name = "clinical-service", path = "/internal/clinical")
public interface ClinicalClient {

    @GetMapping("/visits/{id}")
    ApiResponse<VisitSummaryResponse> getVisit(@PathVariable("id") Long id);

    @GetMapping("/visits")
    ApiResponse<List<VisitSummaryResponse>> getVisits(@RequestParam("ids") List<Long> ids);

    /** Chỉ dịch vụ COMPLETED, đơn giá đã chốt lúc chỉ định. */
    @GetMapping("/visits/{id}/billable-services")
    ApiResponse<List<BillableServiceResponse>> getBillableServices(@PathVariable("id") Long id);
}
