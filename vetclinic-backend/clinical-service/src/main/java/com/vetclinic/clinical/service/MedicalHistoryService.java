package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.response.MedicalHistoryEntry;
import com.vetclinic.common.dto.PageResponse;

/** Bệnh án điện tử trọn đời của thú cưng (UC-38, UC-39, FR-67, FR-68). */
public interface MedicalHistoryService {

    /**
     * Timeline các đợt khám, mới nhất trước. Nhân sự y tế và lễ tân thấy mọi đợt khám; chủ nuôi chỉ thấy thú cưng của
     * mình và chỉ các đợt đã hoàn tất (COMPLETED); thú cưng của người khác trả PET_NOT_FOUND.
     */
    PageResponse<MedicalHistoryEntry> history(Long petId, int page, int size);
}
