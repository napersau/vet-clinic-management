package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.dto.request.DiscountRequest;
import com.vetclinic.billing.dto.response.InvoiceResponse;
import com.vetclinic.billing.dto.response.InvoiceSummaryResponse;
import com.vetclinic.common.dto.PageResponse;

import java.time.LocalDate;

/** Hóa đơn viện phí đa dòng (SM-05): lập từ sự kiện chốt ca, chiết khấu, xác nhận thanh toán. */
public interface InvoiceService {

    /**
     * Lập hóa đơn PENDING cho đợt khám vừa được chốt (consumer của visit.closed_to_payment). Idempotent: đã có hóa đơn
     * thì không làm gì. Tiền dịch vụ lấy qua Feign từ clinical (chỉ dòng COMPLETED), tiền thuốc từ đơn PRESCRIBED.
     * Hóa đơn đã HỦY (đợt khám được mở lại rồi chốt lại) được dựng lại thành PENDING, nhưng chỉ khi sự kiện xảy ra SAU lúc hủy
     * (eventOccurredAt), để sự kiện chốt ca cũ bị giao lại không làm sống lại hóa đơn đã hủy.
     */
    void createForVisit(Long visitId, java.time.Instant eventOccurredAt);

    /**
     * Đường cứu hộ khi sự kiện bị mất / vào DLQ: Lễ tân / Admin lập hóa đơn cho đợt khám đang WAITING_PAYMENT (hoặc ca
     * khách bỏ về đã COMPLETED). Đã có hóa đơn thì trả lại hóa đơn đó.
     */
    InvoiceResponse generateForVisit(Long visitId);

    PageResponse<InvoiceSummaryResponse> search(PaymentStatus status, String code, Long visitId, LocalDate fromDate,
                                                LocalDate toDate, boolean ascending, int page, int size);

    InvoiceResponse getById(Long id);

    InvoiceResponse getByVisit(Long visitId);

    /** UC-34: nhập chiết khấu cho hóa đơn PENDING, không vượt tổng viện phí (EX-14). */
    InvoiceResponse applyDiscount(Long id, DiscountRequest request);

    /**
     * UC-35: xác nhận đã thu tiền. PENDING -> PAID, ghi thu ngân và thời điểm. Ca không có đơn thuốc (hoặc đơn đã hủy)
     * hoàn tất ngay: phát PaymentAndDispenseCompletedEvent. Có đơn thì chờ xuất thuốc (UC-36).
     */
    InvoiceResponse confirmPayment(Long id);

    /** SM-05: hủy hóa đơn PENDING do sai sót nghiệp vụ (bắt buộc lý do). Hóa đơn đã thu tiền không hủy được. */
    InvoiceResponse cancel(Long id, String reason);

    /**
     * Cho clinical-service khi mở lại đợt khám: hủy hóa đơn PENDING của đợt khám để bác sĩ điều chỉnh. Không có hóa đơn hoặc
     * đã hủy thì không làm gì (idempotent, gọi lại an toàn); đã thu tiền thì từ chối (INVOICE_ALREADY_PAID).
     */
    void cancelForReopen(Long visitId);
}
