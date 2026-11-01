package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.request.CreatePetRequest;
import com.vetclinic.clinical.dto.request.UpdatePetRequest;
import com.vetclinic.clinical.dto.response.PetResponse;
import com.vetclinic.clinical.dto.response.PetSummaryResponse;
import com.vetclinic.common.dto.PageResponse;

import java.util.Collection;
import java.util.List;

public interface PetService {

    /** UC-12 / UC-18: chủ nuôi tự thêm, hoặc lễ tân thêm hộ. */
    PetResponse create(CreatePetRequest request);

    PetResponse update(Long id, UpdatePetRequest request);

    PetResponse getById(Long id);

    /** Chủ nuôi chỉ thấy thú cưng của mình. Lọc theo chủ nuôi và từ khóa (tên, mã hồ sơ). */
    PageResponse<PetResponse> search(Long ownerId, String keyword, int page, int size);

    /** Cho appointment-service kiểm tra thú cưng tồn tại và thuộc đúng chủ nuôi. */
    PetSummaryResponse getSummary(Long id);

    /** Như getSummary nhưng theo lô; id không tồn tại bị bỏ qua (làm giàu danh sách lịch hẹn). */
    List<PetSummaryResponse> getSummaries(Collection<Long> ids);
}
