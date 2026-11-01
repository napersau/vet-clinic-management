package com.vetclinic.clinical.service;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.AssignDoctorRequest;
import com.vetclinic.clinical.dto.request.CheckInRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.response.VisitHistoryResponse;
import com.vetclinic.clinical.dto.response.VisitResponse;
import com.vetclinic.common.dto.PageResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Vòng đời một đợt khám ngoại trú hai pha (SM-01). Hiện có phần tiếp nhận, hàng đợi, phân công và hủy (NV1);
 * khám hai giai đoạn (NV2), cận lâm sàng (NV3), thanh toán / mở lại ca (NV4, NV5) bổ sung sau.
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

    /** EX-03 / FR-26 / FR-34 / FR-45: hủy đợt khám. */
    VisitResponse cancel(Long id, ReasonRequest request);
}
