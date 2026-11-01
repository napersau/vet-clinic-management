package com.vetclinic.billing.service;

import com.vetclinic.billing.client.ClinicalClient;
import com.vetclinic.billing.dto.response.BillableServiceResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cửa duy nhất để hỏi clinical-service. KHÔNG gọi trong @Transactional (quy chuẩn Feign). Hai kiểu:
 * require* = bắt buộc (lỗi thì ném, dùng khi quyết định nghiệp vụ như quyền kê đơn, lập hóa đơn);
 * tryFind* = làm giàu hiển thị (tên thú cưng, mã ca khám), clinical tạm sập thì trả rỗng chứ không làm hỏng màn hình.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitLookup {

    private final ClinicalClient clinicalClient;

    public VisitSummaryResponse require(Long visitId) {
        ApiResponse<VisitSummaryResponse> response = clinicalClient.getVisit(visitId);
        VisitSummaryResponse visit = response == null ? null : response.getData();
        if (visit == null) {
            throw new AppException(ErrorCode.VISIT_NOT_FOUND);
        }
        return visit;
    }

    public VisitSummaryResponse tryFind(Long visitId) {
        try {
            return require(visitId);
        } catch (RuntimeException e) {
            log.warn("Không lấy được thông tin đợt khám {} từ clinical-service: {}", visitId, e.getMessage());
            return null;
        }
    }

    public Map<Long, VisitSummaryResponse> tryFindAll(Collection<Long> visitIds) {
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<VisitSummaryResponse>> response = clinicalClient.getVisits(List.copyOf(visitIds));
            List<VisitSummaryResponse> visits = response == null || response.getData() == null
                    ? List.of() : response.getData();
            return visits.stream().collect(Collectors.toMap(VisitSummaryResponse::id, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("Không lấy được thông tin {} đợt khám từ clinical-service: {}", visitIds.size(), e.getMessage());
            return Map.of();
        }
    }

    public List<BillableServiceResponse> billableServices(Long visitId) {
        ApiResponse<List<BillableServiceResponse>> response = clinicalClient.getBillableServices(visitId);
        if (response == null || response.getData() == null) {
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return response.getData();
    }
}
