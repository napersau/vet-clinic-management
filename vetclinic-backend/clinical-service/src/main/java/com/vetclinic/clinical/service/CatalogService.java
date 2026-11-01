package com.vetclinic.clinical.service;

import com.vetclinic.clinical.dto.request.CreateServiceRequest;
import com.vetclinic.clinical.dto.request.LabTestItemRequest;
import com.vetclinic.clinical.dto.request.ReferenceRangeRequest;
import com.vetclinic.clinical.dto.request.UpdateLabTestItemRequest;
import com.vetclinic.clinical.dto.request.SpeciesRequest;
import com.vetclinic.clinical.dto.request.UpdateServiceRequest;
import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.dto.response.ServiceResponse;
import com.vetclinic.clinical.dto.response.SpeciesResponse;

import java.util.List;

/** Danh mục (Master Data): loài, dịch vụ kỹ thuật, chỉ số xét nghiệm kèm khoảng tham chiếu theo loài. */
public interface CatalogService {

    List<SpeciesResponse> listSpecies();

    SpeciesResponse createSpecies(SpeciesRequest request);

    SpeciesResponse updateSpecies(Long id, SpeciesRequest request);

    /** activeOnly = true cho Bác sĩ chọn khi chỉ định; Admin xem cả dịch vụ đã ngừng dùng. */
    List<ServiceResponse> listServices(boolean activeOnly);

    ServiceResponse createService(CreateServiceRequest request);

    ServiceResponse updateService(Long id, UpdateServiceRequest request);

    List<LabTestItemResponse> listLabTestItems();

    /** UC-05 (+UC-06 nếu có ranges): thêm chỉ số xét nghiệm. */
    LabTestItemResponse createLabTestItem(LabTestItemRequest request);

    /** UC-05: sửa tên, đơn vị, mô tả (mã bất biến). */
    LabTestItemResponse updateLabTestItem(Long id, UpdateLabTestItemRequest request);

    /**
     * UC-06: đặt khoảng tham chiếu của một chỉ số cho một loài (thêm mới hoặc sửa). Kết quả đã nhập trước đó giữ nguyên
     * snapshot khoảng cũ (BR-24).
     */
    LabTestItemResponse saveReferenceRange(Long labTestItemId, Long speciesId, ReferenceRangeRequest request);

    /** Các chỉ số của một dịch vụ xét nghiệm; dịch vụ chưa cấu hình thì được coi là gồm mọi chỉ số. */
    List<LabTestItemResponse> listServiceLabTestItems(Long serviceId);

    /** Admin đặt dịch vụ xét nghiệm gồm những chỉ số nào (thay toàn bộ; rỗng = mọi chỉ số). */
    List<LabTestItemResponse> setServiceLabTestItems(Long serviceId, List<Long> labTestItemIds);
}
