package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.repository.ServiceLabTestItemRepository;
import com.vetclinic.clinical.dto.response.ReferenceRangeResponse;
import com.vetclinic.clinical.dto.response.LabTestItemResponse;
import com.vetclinic.clinical.constant.LabEvaluation;
import com.vetclinic.clinical.constant.QueueState;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.LabResultItemRequest;
import com.vetclinic.clinical.dto.request.LabResultRequest;
import com.vetclinic.clinical.dto.response.QueueItemResponse;
import com.vetclinic.clinical.dto.response.ServiceDetailResponse;
import com.vetclinic.clinical.entity.ImagingAttachment;
import com.vetclinic.clinical.entity.ImagingResult;
import com.vetclinic.clinical.entity.LabTestItem;
import com.vetclinic.clinical.entity.LabTestReferenceRange;
import com.vetclinic.clinical.entity.LabTestResult;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import com.vetclinic.clinical.entity.VisitServiceDetailHistory;
import com.vetclinic.clinical.repository.ImagingAttachmentRepository;
import com.vetclinic.clinical.repository.ImagingResultRepository;
import com.vetclinic.clinical.repository.LabTestItemRepository;
import com.vetclinic.clinical.repository.LabTestReferenceRangeRepository;
import com.vetclinic.clinical.repository.LabTestResultRepository;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailHistoryRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import com.vetclinic.clinical.service.ImagingStorage;
import com.vetclinic.clinical.service.ParaclinicService;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.clinical.service.VisitSupport;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import com.vetclinic.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Các thao tác ghi dùng transaction READ_COMMITTED và khóa dòng theo thứ tự: chỉ định -> đợt khám.
 * Lý do: hai kỹ thuật viên (Lab và CĐHA) có thể cùng hoàn tất hai dịch vụ cuối của một ca. Với mức cô lập mặc định
 * REPEATABLE READ, người đến sau chờ khóa đợt khám nhưng vẫn đếm theo ảnh chụp cũ nên không thấy dịch vụ kia đã xong,
 * và ca kẹt mãi ở WAITING_RESULTS. READ_COMMITTED + khóa trước khi đếm khiến người đến sau luôn thấy kết quả mới nhất.
 */
@Slf4j
@Service
public class ParaclinicServiceImpl implements ParaclinicService {

    private static final String POSTPONED = "POSTPONED";
    private static final String RECALL = "RECALL";
    private static final int MAX_FILES = 10;

    private final VisitServiceDetailRepository detailRepository;
    private final VisitServiceDetailHistoryRepository detailHistoryRepository;
    private final PetVisitRepository visitRepository;
    private final LabTestItemRepository labTestItemRepository;
    private final LabTestReferenceRangeRepository rangeRepository;
    private final ServiceLabTestItemRepository serviceLabItemRepository;
    private final LabTestResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingAttachmentRepository attachmentRepository;
    private final VisitSupport support;
    private final VisitAssembler assembler;
    private final ImagingStorage storage;
    private final TransactionTemplate readCommittedTemplate;

    public ParaclinicServiceImpl(VisitServiceDetailRepository detailRepository,
                                 VisitServiceDetailHistoryRepository detailHistoryRepository,
                                 PetVisitRepository visitRepository, LabTestItemRepository labTestItemRepository,
                                 LabTestReferenceRangeRepository rangeRepository,
                                 ServiceLabTestItemRepository serviceLabItemRepository,
                                 LabTestResultRepository labResultRepository,
                                 ImagingResultRepository imagingResultRepository,
                                 ImagingAttachmentRepository attachmentRepository, VisitSupport support,
                                 VisitAssembler assembler, ImagingStorage storage,
                                 PlatformTransactionManager transactionManager) {
        this.detailRepository = detailRepository;
        this.detailHistoryRepository = detailHistoryRepository;
        this.visitRepository = visitRepository;
        this.labTestItemRepository = labTestItemRepository;
        this.rangeRepository = rangeRepository;
        this.serviceLabItemRepository = serviceLabItemRepository;
        this.labResultRepository = labResultRepository;
        this.imagingResultRepository = imagingResultRepository;
        this.attachmentRepository = attachmentRepository;
        this.support = support;
        this.assembler = assembler;
        this.storage = storage;
        this.readCommittedTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private record Ctx(VisitServiceDetail detail, PetVisit visit) {
    }

    // ================================================================== hàng đợi

    @Override
    @Transactional(readOnly = true)
    public List<QueueItemResponse> queue(ServiceCategory category) {
        if (!category.isParaclinical()) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Chỉ có hàng đợi cho xét nghiệm và chẩn đoán hình ảnh");
        }
        List<VisitServiceDetail> details = detailRepository.findQueue(category, ServiceDetailStatus.OPEN);
        if (details.isEmpty()) {
            return List.of();
        }
        List<Long> visitIds = details.stream().map(VisitServiceDetail::getVisitId).distinct().toList();
        Map<Long, PetVisit> visits = visitRepository.findWithPetByIdIn(visitIds).stream()
                .collect(Collectors.toMap(PetVisit::getId, v -> v));
        Map<Long, List<VisitServiceDetailHistory>> histories = detailHistoryRepository
                .findByVisitServiceDetailIdInOrderByIdAsc(details.stream().map(VisitServiceDetail::getId).toList())
                .stream().collect(Collectors.groupingBy(VisitServiceDetailHistory::getVisitServiceDetailId));
        Map<Long, List<VisitServiceDetail>> siblings = detailRepository.findByVisitIdInOrderByIdAsc(visitIds).stream()
                .collect(Collectors.groupingBy(VisitServiceDetail::getVisitId));

        ServiceCategory other = category == ServiceCategory.LABORATORY ? ServiceCategory.IMAGING : ServiceCategory.LABORATORY;
        return details.stream()
                .filter(d -> visits.containsKey(d.getVisitId()))
                .map(d -> {
                    PetVisit v = visits.get(d.getVisitId());
                    return new QueueItemResponse(d.getId(), v.getId(), v.getVisitCode(), v.getPet().getId(),
                            v.getPet().getName(), v.getPet().getSpecies().getName(), d.getService().getId(),
                            d.getService().getName(), category, v.isEmergency(), d.getStatus(),
                            queueState(d, histories.getOrDefault(d.getId(), List.of())),
                            otherRoomStatus(other, siblings.getOrDefault(d.getVisitId(), List.of())),
                            d.getNotes(), d.getCreatedAt());
                })
                .sorted(QUEUE_ORDER)
                .toList();
    }

    private static final Comparator<QueueItemResponse> QUEUE_ORDER = Comparator
            // đang thực hiện trước, ca tạm hoãn xếp cuối
            .comparingInt((QueueItemResponse i) -> i.queueState() == QueueState.IN_PROGRESS ? 0
                    : i.queueState() == QueueState.POSTPONED ? 2 : 1)
            // FR-21 / EX-04: ca cấp cứu nhảy lên đầu
            .thenComparing(QueueItemResponse::emergency, Comparator.reverseOrder())
            // EX-07: khách quay lại được gọi ngay sau ca đang làm, không phải xếp lại từ đầu
            .thenComparingInt(i -> i.queueState() == QueueState.NEXT_IN_LINE ? 0 : 1)
            .thenComparing(QueueItemResponse::orderedAt)
            .thenComparing(QueueItemResponse::serviceDetailId);

    private static QueueState queueState(VisitServiceDetail detail, List<VisitServiceDetailHistory> histories) {
        if (detail.getStatus() == ServiceDetailStatus.IN_PROGRESS) {
            return QueueState.IN_PROGRESS;
        }
        String last = lastQueueAction(histories);
        return POSTPONED.equals(last) ? QueueState.POSTPONED : RECALL.equals(last) ? QueueState.NEXT_IN_LINE : QueueState.WAITING;
    }

    /** Hành động gần nhất trong số POSTPONED / RECALL (các hành động khác không ảnh hưởng vị trí trong hàng đợi). */
    private static String lastQueueAction(List<VisitServiceDetailHistory> histories) {
        for (int i = histories.size() - 1; i >= 0; i--) {
            String action = histories.get(i).getAction();
            if (POSTPONED.equals(action) || RECALL.equals(action)) {
                return action;
            }
        }
        return null;
    }

    /** FR-36: huy hiệu liên phòng, vd. "LAB_IN_PROGRESS" khi thú cưng đang lấy máu bên phòng Lab. */
    private static String otherRoomStatus(ServiceCategory other, List<VisitServiceDetail> siblings) {
        List<VisitServiceDetail> inOtherRoom = siblings.stream().filter(s -> s.getService().getCategory() == other).toList();
        if (inOtherRoom.isEmpty()) {
            return null;
        }
        String prefix = other == ServiceCategory.LABORATORY ? "LAB" : "IMAGING";
        if (inOtherRoom.stream().anyMatch(s -> s.getStatus() == ServiceDetailStatus.IN_PROGRESS)) {
            return prefix + "_IN_PROGRESS";
        }
        if (inOtherRoom.stream().anyMatch(s -> s.getStatus() == ServiceDetailStatus.PENDING)) {
            return prefix + "_PENDING";
        }
        return prefix + "_DONE";
    }

    // ================================================================== thao tác trên một chỉ định

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceDetailResponse start(Long detailId) {
        Long actor = SecurityUtils.getCurrentUserId();
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), false);
        requireDetailStatus(c.detail(), ServiceDetailStatus.PENDING, "Chỉ bắt đầu thực hiện được chỉ định đang chờ (PENDING)");
        requireVisitWaitingResults(c.visit());
        if (isPostponed(detailId)) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS,
                    "Ca đang tạm hoãn do vắng mặt, hãy gọi lại (Recall) trước khi bắt đầu");
        }
        setStatus(c.detail(), ServiceDetailStatus.IN_PROGRESS, "START_PROCESSING", actor, null);
        return assembler.toServiceDetail(c.detail());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceDetailResponse postpone(Long detailId, String reason) {
        Long actor = SecurityUtils.getCurrentUserId();
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), false);
        requireDetailStatus(c.detail(), ServiceDetailStatus.PENDING, "Chỉ tạm hoãn được chỉ định đang chờ (PENDING)");
        requireVisitWaitingResults(c.visit());
        if (isPostponed(detailId)) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, "Ca này đã được tạm hoãn trước đó");
        }
        // Giữ nguyên PENDING (chưa hao phí vật tư); chỉ ghi vào audit để đưa sang tab "Ca tạm hoãn do vắng mặt"
        support.recordDetail(detailId, ServiceDetailStatus.PENDING, ServiceDetailStatus.PENDING, POSTPONED, actor,
                blankToNull(reason));
        return assembler.toServiceDetail(c.detail());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceDetailResponse recall(Long detailId) {
        Long actor = SecurityUtils.getCurrentUserId();
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), false);
        requireDetailStatus(c.detail(), ServiceDetailStatus.PENDING, "Chỉ gọi lại được chỉ định đang chờ (PENDING)");
        if (!isPostponed(detailId)) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, "Chỉ gọi lại được ca đang tạm hoãn");
        }
        support.recordDetail(detailId, ServiceDetailStatus.PENDING, ServiceDetailStatus.PENDING, RECALL, actor,
                "Khách đã có mặt trở lại, ghim vào vị trí ưu tiên kế tiếp");
        return assembler.toServiceDetail(c.detail());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceDetailResponse cancel(Long detailId, String reason) {
        Long actor = SecurityUtils.getCurrentUserId();
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), true);
        if (!ServiceDetailStatus.OPEN.contains(c.detail().getStatus())) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS,
                    "Chỉ hủy được chỉ định đang chờ hoặc đang thực hiện");
        }
        setStatus(c.detail(), ServiceDetailStatus.CANCELLED, "CANCELLED", actor, reason.trim());
        // EX-08: dịch vụ này là dịch vụ cuối còn dở dang thì ca chuyển sang RESULTS_READY với kết quả đã có
        support.evaluateParaclinicalCompletion(c.visit(), actor);
        return assembler.toServiceDetail(c.detail());
    }

    // ================================================================== nhập kết quả

    @Override
    @Transactional(readOnly = true)
    public List<LabTestItemResponse> labForm(Long detailId) {
        VisitServiceDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_DETAIL_NOT_FOUND));
        assertOperator(detail, true);
        if (detail.getService().getCategory() != ServiceCategory.LABORATORY) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, "Chỉ định này không phải xét nghiệm");
        }
        PetVisit visit = visitRepository.findWithPetById(detail.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        Long speciesId = visit.getPet().getSpecies().getId();
        Set<Long> configured = new HashSet<>(serviceLabItemRepository.findItemIdsByServiceId(detail.getService().getId()));
        List<LabTestItem> items = labTestItemRepository.findAllByOrderByCodeAsc().stream()
                .filter(i -> configured.isEmpty() || configured.contains(i.getId())).toList();
        Map<Long, List<ReferenceRangeResponse>> ranges = items.isEmpty() ? Map.of()
                : rangeRepository.findByLabTestItemIdIn(items.stream().map(LabTestItem::getId).toList()).stream()
                .filter(r -> r.getSpecies().getId().equals(speciesId))   // BR-23: chỉ khoảng của loài thú cưng này
                .collect(Collectors.groupingBy(r -> r.getLabTestItem().getId(), Collectors.mapping(
                        r -> new ReferenceRangeResponse(r.getSpecies().getId(), r.getSpecies().getCode(), r.getMinValue(),
                                r.getMaxValue()), Collectors.toList())));
        return items.stream().map(i -> new LabTestItemResponse(i.getId(), i.getCode(), i.getName(), i.getUnit(),
                i.getDescription(), ranges.getOrDefault(i.getId(), List.of()))).toList();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceDetailResponse submitLabResults(Long detailId, LabResultRequest request) {
        Long actor = SecurityUtils.getCurrentUserId();
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), false);
        if (c.detail().getService().getCategory() != ServiceCategory.LABORATORY) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, "Chỉ định này không phải xét nghiệm");
        }
        requireDetailStatus(c.detail(), ServiceDetailStatus.IN_PROGRESS,
                "Phải bắt đầu thực hiện (IN_PROGRESS) trước khi nhập kết quả");
        requireVisitWaitingResults(c.visit());

        List<Long> itemIds = request.items().stream().map(LabResultItemRequest::labTestItemId).toList();
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Mỗi chỉ số chỉ được nhập một lần");
        }
        Map<Long, LabTestItem> items = labTestItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(LabTestItem::getId, i -> i));
        // Chỉ nhập các chỉ số thuộc dịch vụ này (chưa cấu hình dòng nào = mọi chỉ số đều được)
        Set<Long> allowed = new HashSet<>(serviceLabItemRepository.findItemIdsByServiceId(c.detail().getService().getId()));
        // BR-23: khoảng tham chiếu phụ thuộc loài của thú cưng
        Long speciesId = c.visit().getPet().getSpecies().getId();

        List<LabTestResult> results = new ArrayList<>();
        for (LabResultItemRequest item : request.items()) {
            LabTestItem labItem = items.get(item.labTestItemId());
            if (labItem == null) {
                throw new AppException(ErrorCode.LAB_ITEM_NOT_FOUND);
            }
            if (!allowed.isEmpty() && !allowed.contains(labItem.getId())) {
                throw new AppException(ErrorCode.LAB_ITEM_NOT_IN_SERVICE, "Chỉ số " + labItem.getCode()
                        + " không thuộc dịch vụ " + c.detail().getService().getName());
            }
            LabTestReferenceRange range = rangeRepository.findByLabTestItemIdAndSpeciesId(labItem.getId(), speciesId)
                    .orElseThrow(() -> new AppException(ErrorCode.LAB_REFERENCE_NOT_FOUND,
                            "Chưa cấu hình khoảng tham chiếu của chỉ số " + labItem.getCode() + " cho loài của thú cưng"));
            results.add(LabTestResult.builder()
                    .visitServiceDetailId(detailId)
                    .labTestItem(labItem)
                    .resultValue(item.value())
                    // BR-24: snapshot khoảng tham chiếu để kết quả cũ không đổi nếu Admin chỉnh cấu hình về sau
                    .minValueSnapshot(range.getMinValue())
                    .maxValueSnapshot(range.getMaxValue())
                    .evaluation(evaluate(item.value(), range))
                    .performedBy(actor)
                    .notes(blankToNull(item.notes()))
                    .build());
        }
        labResultRepository.saveAll(results);

        setStatus(c.detail(), ServiceDetailStatus.COMPLETED, "COMPLETED", actor, null);
        support.evaluateParaclinicalCompletion(c.visit(), actor);
        return assembler.toServiceDetail(c.detail());
    }

    /** LOW khi nhỏ hơn ngưỡng dưới, HIGH khi lớn hơn ngưỡng trên, NORMAL khi nằm trong khoảng (gồm cả hai đầu). */
    static LabEvaluation evaluate(BigDecimal value, LabTestReferenceRange range) {
        if (value.compareTo(range.getMinValue()) < 0) {
            return LabEvaluation.LOW;
        }
        if (value.compareTo(range.getMaxValue()) > 0) {
            return LabEvaluation.HIGH;
        }
        return LabEvaluation.NORMAL;
    }

    /**
     * Không @Transactional ở mức method: tệp được lưu ra đĩa TRƯỚC, rồi mới ghi CSDL trong transaction; nếu ghi CSDL
     * thất bại thì xóa các tệp vừa lưu để không để lại tệp mồ côi.
     */
    @Override
    public ServiceDetailResponse submitImagingResult(Long detailId, String description, String conclusion,
                                                     List<MultipartFile> files) {
        Long actor = SecurityUtils.getCurrentUserId();
        if (SecurityUtils.getCurrentRole().orElse(null) != RoleCode.IMAGING_TECHNICIAN) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (blankToNull(description) == null || blankToNull(conclusion) == null) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Phải nhập mô tả hình ảnh và kết luận");
        }
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE, "Phải đính kèm ít nhất một ảnh");
        }
        if (files.size() > MAX_FILES) {
            throw new AppException(ErrorCode.INVALID_IMAGING_FILE, "Tối đa " + MAX_FILES + " ảnh mỗi kết quả");
        }
        files.forEach(storage::validate); // kiểm tra hết trước khi ghi bất kỳ tệp nào
        VisitServiceDetail preview = detailRepository.findById(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_DETAIL_NOT_FOUND));

        List<ImagingStorage.StoredFile> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                stored.add(storage.store(preview.getVisitId(), file));
            }
            return readCommittedTemplate.execute(tx -> saveImaging(actor, detailId, description.trim(),
                    conclusion.trim(), stored));
        } catch (RuntimeException e) {
            stored.forEach(f -> storage.deleteQuietly(f.relativePath()));
            throw e;
        }
    }

    private ServiceDetailResponse saveImaging(Long actor, Long detailId, String description, String conclusion,
                                              List<ImagingStorage.StoredFile> stored) {
        Ctx c = lockForAction(detailId);
        assertOperator(c.detail(), false);
        if (c.detail().getService().getCategory() != ServiceCategory.IMAGING) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, "Chỉ định này không phải chẩn đoán hình ảnh");
        }
        requireDetailStatus(c.detail(), ServiceDetailStatus.IN_PROGRESS,
                "Phải bắt đầu thực hiện (IN_PROGRESS) trước khi nhập kết quả");
        requireVisitWaitingResults(c.visit());

        ImagingResult result = imagingResultRepository.saveAndFlush(ImagingResult.builder()
                .visitServiceDetailId(detailId).description(description).conclusion(conclusion).performedBy(actor).build());
        attachmentRepository.saveAll(stored.stream().map(f -> ImagingAttachment.builder()
                .imagingResultId(result.getId()).fileUrl(f.relativePath()).fileName(f.originalName())
                .fileSize(f.size()).contentType(f.contentType()).build()).toList());

        setStatus(c.detail(), ServiceDetailStatus.COMPLETED, "COMPLETED", actor, null);
        support.evaluateParaclinicalCompletion(c.visit(), actor);
        return assembler.toServiceDetail(c.detail());
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(Long attachmentId) {
        Long actor = SecurityUtils.getCurrentUserId();
        RoleCode role = SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        ImagingAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ImagingResult result = imagingResultRepository.findById(attachment.getImagingResultId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        VisitServiceDetail detail = detailRepository.findById(result.getVisitServiceDetailId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        PetVisit visit = visitRepository.findWithPetById(detail.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        // Chủ nuôi chỉ tải được ảnh của thú cưng mình; của người khác trả "không tìm thấy"
        if (role == RoleCode.PET_OWNER && !visit.getPet().getOwnerId().equals(actor)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new AttachmentDownload(storage.load(attachment.getFileUrl()), attachment.getContentType(),
                attachment.getFileName());
    }

    // ================================================================== helper

    /** Khóa chỉ định rồi khóa đợt khám (luôn theo thứ tự này). Câu truy vấn khóa là lệnh đầu tiên của transaction. */
    private Ctx lockForAction(Long detailId) {
        VisitServiceDetail detail = detailRepository.findByIdForUpdate(detailId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_DETAIL_NOT_FOUND));
        PetVisit visit = support.lock(detail.getVisitId());
        return new Ctx(detail, visit);
    }

    /** Kỹ thuật viên chỉ thao tác phòng của mình; Bác sĩ chỉ được hủy chỉ định (đổi hướng xử lý). */
    private void assertOperator(VisitServiceDetail detail, boolean allowDoctor) {
        RoleCode role = SecurityUtils.getCurrentRole().orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        ServiceCategory category = detail.getService().getCategory();
        boolean allowed = (category == ServiceCategory.LABORATORY && role == RoleCode.LAB_TECHNICIAN)
                || (category == ServiceCategory.IMAGING && role == RoleCode.IMAGING_TECHNICIAN)
                || (allowDoctor && role == RoleCode.VETERINARIAN);
        if (!allowed) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void setStatus(VisitServiceDetail detail, ServiceDetailStatus to, String action, Long actor, String reason) {
        ServiceDetailStatus from = detail.getStatus();
        detail.setStatus(to);
        detailRepository.save(detail);
        support.recordDetail(detail.getId(), from, to, action, actor, reason);
    }

    private void requireDetailStatus(VisitServiceDetail detail, ServiceDetailStatus expected, String message) {
        if (detail.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, message);
        }
    }

    private void requireVisitWaitingResults(PetVisit visit) {
        if (visit.getStatus() != VisitStatus.WAITING_RESULTS) {
            throw new AppException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Đợt khám không còn ở trạng thái chờ kết quả cận lâm sàng");
        }
    }

    private boolean isPostponed(Long detailId) {
        List<VisitServiceDetailHistory> histories = detailHistoryRepository
                .findByVisitServiceDetailIdInOrderByIdAsc(Set.of(detailId));
        return POSTPONED.equals(lastQueueAction(histories));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
