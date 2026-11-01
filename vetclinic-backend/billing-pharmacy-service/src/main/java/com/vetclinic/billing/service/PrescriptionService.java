package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.dto.request.PrescriptionRequest;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.common.dto.PageResponse;

import java.util.Collection;
import java.util.List;

/** Đơn thuốc điện tử (SM-06) và xuất thuốc trừ kho (UC-36). */
public interface PrescriptionService {

    /**
     * UC-31: Bác sĩ phụ trách kê đơn khi ca đang IN_PROGRESS (gọi TRƯỚC close-visit). Chốt đơn giá hiện tại (BR-26),
     * chặn kê vượt tồn kho (EX-13) nhưng chưa trừ kho (BR-29). Đợt khám đã có đơn đã hủy thì dùng lại đúng đơn đó.
     */
    PrescriptionResponse create(PrescriptionRequest request);

    /** Thay toàn bộ nội dung đơn đang PRESCRIBED, chỉ khi ca còn IN_PROGRESS và chưa có hóa đơn. */
    PrescriptionResponse update(Long id, PrescriptionRequest request);

    /** Chủ nuôi chỉ xem được đơn của thú cưng mình (UC-39); đơn của người khác trả PRESCRIPTION_NOT_FOUND. */
    PrescriptionResponse getById(Long id);

    PrescriptionResponse getByVisit(Long visitId);

    /** Cho clinical-service dựng bệnh án trọn đời: đơn (chưa hủy) của nhiều đợt khám, theo lô. Không kiểm tra người xem. */
    List<PrescriptionResponse> findByVisits(Collection<Long> visitIds);

    /** awaitingDispense = đơn đã thanh toán, đang chờ phát (quầy thuốc). */
    PageResponse<PrescriptionSummaryResponse> search(PrescriptionStatus status, Long visitId, boolean awaitingDispense,
                                                      int page, int size);

    /**
     * Hủy đơn (PRESCRIBED -> CANCELLED), không biến động kho. Bác sĩ phụ trách hủy khi ca còn IN_PROGRESS; Lễ tân / Admin
     * hủy khi hóa đơn còn PENDING (khách xin toa mua ngoài): các dòng thuốc bị gỡ khỏi hóa đơn và tổng được tính lại.
     */
    PrescriptionResponse cancel(Long id);

    /**
     * UC-36: xuất thuốc. Chỉ khi hóa đơn PAID (BR-28) và đơn còn PRESCRIBED (EX-16); trừ kho nguyên tử và rollback toàn bộ
     * nếu một thuốc không đủ (EX-15); xong thì phát PaymentAndDispenseCompletedEvent để clinical hoàn tất ca.
     */
    PrescriptionResponse dispense(Long id);
}
