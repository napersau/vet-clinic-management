package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.response.PetSummaryResponse;
import com.vetclinic.clinical.service.PetService;
import com.vetclinic.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint cho các service khác gọi qua OpenFeign. Nằm dưới /internal/**: không cần header người dùng và Gateway
 * không định tuyến ra ngoài, nên chỉ truy cập được trong mạng Docker.
 * Các endpoint đợt khám cho billing (/visits/{id}, /visits?ids=, /visits/{id}/billable-services) thêm ở phần 2.
 */
@RestController
@RequestMapping("/internal/clinical")
@RequiredArgsConstructor
public class InternalClinicalController {

    private final PetService petService;

    /** appointment-service kiểm tra thú cưng tồn tại và thuộc đúng chủ nuôi khi đặt lịch. */
    @GetMapping("/pets/{id}")
    public ResponseEntity<ApiResponse<PetSummaryResponse>> pet(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(petService.getSummary(id)));
    }

    /** appointment-service làm giàu danh sách lịch hẹn bằng tên thú cưng, theo lô. */
    @GetMapping("/pets")
    public ResponseEntity<ApiResponse<List<PetSummaryResponse>>> pets(@RequestParam("ids") List<Long> ids) {
        return ResponseEntity.ok(ApiResponse.success(petService.getSummaries(ids)));
    }
}
