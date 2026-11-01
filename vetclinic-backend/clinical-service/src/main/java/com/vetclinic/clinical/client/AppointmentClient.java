package com.vetclinic.clinical.client;

import com.vetclinic.clinical.dto.response.AppointmentSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "appointment-service", path = "/internal/appointments")
public interface AppointmentClient {

    @GetMapping("/{id}")
    ApiResponse<AppointmentSummaryResponse> getAppointment(@PathVariable("id") Long id);
}
