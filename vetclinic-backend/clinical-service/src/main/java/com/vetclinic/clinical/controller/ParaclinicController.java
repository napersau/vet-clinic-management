package com.vetclinic.clinical.controller;

import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.dto.request.LabResultRequest;
import com.vetclinic.clinical.dto.request.OptionalReasonRequest;
import com.vetclinic.clinical.dto.request.ReasonRequest;
import com.vetclinic.clinical.dto.response.QueueItemResponse;
import com.vetclinic.clinical.dto.response.ServiceDetailResponse;
import com.vetclinic.clinical.service.ParaclinicService;
import com.vetclinic.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Hai hàng đợi cận lâm sàng độc lập (Lab, CĐHA) và thao tác trên từng chỉ định. */
@RestController
@RequestMapping("/api/v1/clinical")
@RequiredArgsConstructor
public class ParaclinicController {

    private final ParaclinicService paraclinicService;

    @GetMapping("/lab-queue")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<QueueItemResponse>>> labQueue() {
        return ResponseEntity.ok(ApiResponse.success(paraclinicService.queue(ServiceCategory.LABORATORY)));
    }

    @GetMapping("/imaging-queue")
    @PreAuthorize("hasAnyRole('IMAGING_TECHNICIAN','VETERINARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<List<QueueItemResponse>>> imagingQueue() {
        return ResponseEntity.ok(ApiResponse.success(paraclinicService.queue(ServiceCategory.IMAGING)));
    }

    /** Thú cưng đã vào phòng, bắt đầu thực hiện. */
    @PostMapping("/service-details/{id}/start")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','IMAGING_TECHNICIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> start(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu thực hiện", paraclinicService.start(id)));
    }

    /** EX-06 / EX-09: tạm hoãn do khách vắng mặt hoặc thú cưng đang ở phòng kia. */
    @PostMapping("/service-details/{id}/postpone")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','IMAGING_TECHNICIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> postpone(
            @PathVariable Long id, @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạm hoãn, chuyển sang tab ca tạm hoãn",
                paraclinicService.postpone(id, request == null ? null : request.reason())));
    }

    /** EX-07: khách quay lại, ghim vào vị trí ưu tiên kế tiếp. */
    @PostMapping("/service-details/{id}/recall")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','IMAGING_TECHNICIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> recall(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Đã ghim vào vị trí ưu tiên kế tiếp", paraclinicService.recall(id)));
    }

    /** EX-08: hủy chỉ định (khách xin hủy, thiết bị sự cố, hoặc Bác sĩ đổi hướng xử lý). */
    @PostMapping("/service-details/{id}/cancel")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','IMAGING_TECHNICIAN','VETERINARIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> cancel(@PathVariable Long id,
                                                                     @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy chỉ định", paraclinicService.cancel(id, request.reason())));
    }

    /** Form nhập kết quả: chỉ các chỉ số của dịch vụ, kèm khoảng tham chiếu của loài thú cưng. */
    @GetMapping("/service-details/{id}/lab-form")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','VETERINARIAN')")
    public ResponseEntity<ApiResponse<List<LabTestItemResponse>>> labForm(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(paraclinicService.labForm(id)));
    }

    /** UC-25: nhập chỉ số xét nghiệm; hệ thống tự đối chiếu khoảng tham chiếu của loài. */
    @PostMapping("/service-details/{id}/lab-results")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> labResults(@PathVariable Long id,
                                                                         @Valid @RequestBody LabResultRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu kết quả xét nghiệm", paraclinicService.submitLabResults(id, request)));
    }

    /** UC-26: multipart/form-data gồm description, conclusion và một hoặc nhiều tệp ảnh trong phần "files". */
    @PostMapping(path = "/service-details/{id}/imaging-result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('IMAGING_TECHNICIAN')")
    public ResponseEntity<ApiResponse<ServiceDetailResponse>> imagingResult(
            @PathVariable Long id,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String conclusion,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu kết quả chẩn đoán hình ảnh",
                paraclinicService.submitImagingResult(id, description, conclusion, files)));
    }

    @GetMapping("/imaging-attachments/{id}/file")
    @PreAuthorize("hasAnyRole('PET_OWNER','RECEPTIONIST','VETERINARIAN','IMAGING_TECHNICIAN','ADMIN')")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        ParaclinicService.AttachmentDownload file = paraclinicService.downloadAttachment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }
}
