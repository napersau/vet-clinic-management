package com.vetclinic.clinical.service;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.CloseVisitRequest;
import com.vetclinic.clinical.dto.request.OrderServicesRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.request.StartExamRequest;
import com.vetclinic.clinical.dto.response.BillableServiceResponse;
import com.vetclinic.clinical.dto.response.VisitHistoryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.clinical.dto.response.VisitSummaryResponse;
import com.vetclinic.common.dto.PageResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Vòng đời một đợt khám ngoại trú hai pha (SM-01): tiếp nhận, hàng đợi, phân công, hủy (NV1) và khám hai giai đoạn (NV2).
 * Mở lại ca đã chốt (NV5) bổ sung sau.
 */
public interface VisitService {

    /** UC-19: tiếp nhận khách có hẹn (appointmentId) hoặc vãng lai (petId). Tạo đợt khám WAITING. */
    VisitResponse checkIn(CheckInRequest request);

    /** UC-20: hàng đợi chung tại sảnh: cấp cứu -> có hẹn đúng giờ -> vãng lai và khách đến trễ. */
    List<VisitResponse> waitingQueue();

    /** Danh sách đợt khám có lọc (bảng điều phối): trạng thái, ngày check-in theo giờ phòng khám, bác sĩ, thú cưng, mã ca. Mới nhất trước. */
    PageResponse<VisitResponse> search(VisitStatus status, LocalDate date, Long doctorId, Long petId, String keyword,
                                       int page, int size);

    VisitResponse getById(Long id);

    List<VisitHistoryResponse> getHistory(Long id);

    /** UC-21: Lễ tân phân công Bác sĩ (kiểm tra BUSY và Khóa ưu tiên). Ca vẫn WAITING cho tới khi Bác sĩ bấm bắt đầu. */
    VisitResponse assignDoctor(Long id, AssignDoctorRequest request);

    /** UC-22: Bác sĩ bắt đầu khám giai đoạn 1. WAITING -> IN_PROGRESS. */
    VisitResponse startExam(Long id, StartExamRequest request);

    /** UC-23: chỉ định dịch vụ. Có Lab / CĐHA thì IN_PROGRESS -> WAITING_RESULTS và giải phóng Bác sĩ. */
    VisitResponse orderServices(Long id, OrderServicesRequest request);

    /** UC-30: mời ca đã có kết quả vào phòng (GĐ2), cũng là Recall sau khi tạm hoãn. RESULTS_READY -> IN_PROGRESS. */
    VisitResponse callBack(Long id);

    /** EX-10: khách vắng khi gọi vào GĐ2. Giữ RESULTS_READY nhưng giải phóng Khóa ưu tiên của Bác sĩ. */
    VisitResponse postponeCallback(Long id);

    /** UC-33: chốt chuyên môn, khóa hồ sơ. IN_PROGRESS -> WAITING_PAYMENT, phát sự kiện để billing lập hóa đơn. */
    VisitResponse closeVisit(Long id, CloseVisitRequest request);

    /** EX-11: khách bỏ về hẳn không đọc kết quả. RESULTS_READY -> COMPLETED, ghi nợ viện phí. */
    VisitResponse abandon(Long id);

    /** EX-03 / FR-26 / FR-34 / FR-45: hủy đợt khám. */
    VisitResponse cancel(Long id, ReasonRequest request);

    /** Danh sách ca của Bác sĩ đang đăng nhập: chờ vào phòng, đang khám, chờ tái khám. */
    List<VisitResponse> myVisits();

    // ------------------------------------------------------------------ không cần người dùng đăng nhập (/internal, sự kiện)

    /** Consumer của PaymentAndDispenseCompletedEvent: WAITING_PAYMENT -> COMPLETED. Idempotent. */
    void completeAfterPayment(Long visitId, Long actorUserId);

    /** Cho billing-pharmacy-service lập hóa đơn: chỉ dịch vụ COMPLETED. */
    List<BillableServiceResponse> billableServices(Long visitId);

    /** Cho billing-pharmacy-service: thông tin rút gọn của một đợt khám (quyền kê đơn, quyền xem của chủ nuôi). */
    VisitSummaryResponse summary(Long visitId);

    /** Như summary nhưng theo lô; id không tồn tại thì bị bỏ qua (dùng làm giàu danh sách). */
    List<VisitSummaryResponse> summaries(Collection<Long> visitIds);
}
