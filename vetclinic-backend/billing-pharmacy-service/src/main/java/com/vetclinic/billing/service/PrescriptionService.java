package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.common.dto.PageResponse;

/**
 * Đơn thuốc điện tử (SM-06). Xuất thuốc trừ kho (dispense, UC-36) và hàng đợi phát thuốc (awaitingDispense) thêm ở NV4b
 * vì cần hóa đơn đã thanh toán; tra cứu theo lô cho bệnh án trọn đời (findByVisits) thêm ở NV5.
 */
public interface PrescriptionService {

    /**
     * UC-31: Bác sĩ phụ trách kê đơn khi ca đang IN_PROGRESS (gọi TRƯỚC close-visit). Chốt đơn giá hiện tại (BR-26),
     * chặn kê vượt tồn kho (EX-13) nhưng chưa trừ kho (BR-29). Đợt khám đã có đơn đã hủy thì dùng lại đúng đơn đó.
     */
    PrescriptionResponse create(PrescriptionRequest request);

    /** Thay toàn bộ nội dung đơn đang PRESCRIBED, chỉ khi ca còn IN_PROGRESS. */
    PrescriptionResponse update(Long id, PrescriptionRequest request);

    /** Chủ nuôi chỉ xem được đơn của thú cưng mình (UC-39); đơn của người khác trả PRESCRIPTION_NOT_FOUND. */
    PrescriptionResponse getById(Long id);

    PrescriptionResponse getByVisit(Long visitId);

    PageResponse<PrescriptionSummaryResponse> search(PrescriptionStatus status, Long visitId, int page, int size);

    /**
     * Hủy đơn (PRESCRIBED -> CANCELLED), không biến động kho. Bác sĩ phụ trách hủy khi ca còn IN_PROGRESS. Lễ tân / Admin
     * hủy khi hóa đơn còn PENDING (khách xin toa mua ngoài, gỡ dòng thuốc khỏi hóa đơn) thêm ở NV4b; khi chưa có hóa đơn
     * thì chỉ bác sĩ phụ trách hủy được.
     */
    PrescriptionResponse cancel(Long id);
}
