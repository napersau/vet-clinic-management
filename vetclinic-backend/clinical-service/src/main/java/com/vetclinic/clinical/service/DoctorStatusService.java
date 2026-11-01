package com.vetclinic.clinical.service;

import com.vetclinic.clinical.constant.DoctorStatus;
import com.vetclinic.clinical.dto.response.DoctorStatusResponse;

import java.util.List;

/**
 * Trạng thái phục vụ tức thời của Bác sĩ, TÍNH ĐỘNG từ các ca khám, không lưu cột trạng thái (state machine SM-02):
 * <ul>
 *   <li>BUSY: đang có ca IN_PROGRESS, hoặc đã được phân công một ca WAITING (chưa vào phòng).
 *       Vế thứ hai bổ sung so với công thức trong tài liệu, nếu không hai Lễ tân có thể cùng phân công một bác sĩ.</li>
 *   <li>AVAILABLE_PRIORITY_CALLBACK: rảnh nhưng có ca RESULTS_READY chưa bị tạm hoãn (Khóa ưu tiên, EX-05).</li>
 *   <li>AVAILABLE: rảnh hoàn toàn.</li>
 * </ul>
 */
public interface DoctorStatusService {

    DoctorStatus statusOf(Long doctorId);

    /** Bảng điều phối của Lễ tân: mọi Bác sĩ đang ACTIVE kèm số phòng và trạng thái. */
    List<DoctorStatusResponse> board();
}
