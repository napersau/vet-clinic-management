package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.constant.LabEvaluation;
import com.vetclinic.clinical.constant.QueueState;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.request.LabResultItemRequest;
import com.vetclinic.clinical.dto.request.LabResultRequest;
import com.vetclinic.clinical.dto.response.QueueItemResponse;
import com.vetclinic.clinical.dto.response.ServiceDetailResponse;
import com.vetclinic.clinical.entity.ClinicService;
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
import com.vetclinic.clinical.repository.PetVisitStatusHistoryRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailHistoryRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import com.vetclinic.clinical.service.ImagingStorage;
import com.vetclinic.clinical.service.VisitAssembler;
import com.vetclinic.clinical.service.VisitSupport;
import com.vetclinic.common.event.DoctorPriorityCallbackEvent;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.vetclinic.clinical.testsupport.TestData.CLOCK;
import static com.vetclinic.clinical.testsupport.TestData.DOCTOR;
import static com.vetclinic.clinical.testsupport.TestData.IMAGING_TECH;
import static com.vetclinic.clinical.testsupport.TestData.LAB_TECH;
import static com.vetclinic.clinical.testsupport.TestData.assertError;
import static com.vetclinic.clinical.testsupport.TestData.cat;
import static com.vetclinic.clinical.testsupport.TestData.detail;
import static com.vetclinic.clinical.testsupport.TestData.dog;
import static com.vetclinic.clinical.testsupport.TestData.loginAs;
import static com.vetclinic.clinical.testsupport.TestData.pet;
import static com.vetclinic.clinical.testsupport.TestData.service;
import static com.vetclinic.clinical.testsupport.TestData.visit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParaclinicServiceImplTest {

    private static final ClinicService CBC = service(5L, "LAB_CBC", ServiceCategory.LABORATORY, "250000.00");
    private static final ClinicService ULTRASOUND = service(6L, "IMG_US_AB", ServiceCategory.IMAGING, "200000.00");

    @Mock
    private VisitServiceDetailRepository detailRepository;
    @Mock
    private VisitServiceDetailHistoryRepository detailHistoryRepository;
    @Mock
    private PetVisitRepository visitRepository;
    @Mock
    private PetVisitStatusHistoryRepository historyRepository;
    @Mock
    private LabTestItemRepository labTestItemRepository;
    @Mock
    private LabTestReferenceRangeRepository rangeRepository;
    @Mock
    private com.vetclinic.clinical.repository.ServiceLabTestItemRepository serviceLabItemRepository;
    @Mock
    private LabTestResultRepository labResultRepository;
    @Mock
    private ImagingResultRepository imagingResultRepository;
    @Mock
    private ImagingAttachmentRepository attachmentRepository;
    @Mock
    private ImagingStorage storage;
    @Mock
    private ApplicationEventPublisher publisher;

    private ParaclinicServiceImpl service;

    @BeforeEach
    void setUp() {
        VisitSupport support = new VisitSupport(visitRepository, historyRepository, detailRepository,
                detailHistoryRepository, publisher, CLOCK);
        VisitAssembler assembler = new VisitAssembler(detailRepository, labResultRepository, imagingResultRepository,
                attachmentRepository);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ParaclinicServiceImpl(detailRepository, detailHistoryRepository, visitRepository,
                labTestItemRepository, rangeRepository, serviceLabItemRepository, labResultRepository, imagingResultRepository,
                attachmentRepository, support, assembler, storage, tm);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================== hàng đợi

    @Test
    void queue_ordersByInProgressThenEmergencyThenRecalledThenFifoAndPostponedLast_withCrossRoomBadge() {
        PetVisit normal = visit(10L, VisitStatus.WAITING_RESULTS, DOCTOR);
        PetVisit emergency = visit(11L, VisitStatus.WAITING_RESULTS, DOCTOR);
        emergency.setEmergency(true);
        PetVisit recalled = visit(12L, VisitStatus.WAITING_RESULTS, DOCTOR);
        PetVisit postponed = visit(13L, VisitStatus.WAITING_RESULTS, DOCTOR);
        PetVisit working = visit(14L, VisitStatus.WAITING_RESULTS, DOCTOR);

        VisitServiceDetail d1 = detail(1L, 10L, CBC, ServiceDetailStatus.PENDING);
        VisitServiceDetail d2 = detail(2L, 11L, CBC, ServiceDetailStatus.PENDING);
        VisitServiceDetail d3 = detail(3L, 12L, CBC, ServiceDetailStatus.PENDING);
        VisitServiceDetail d4 = detail(4L, 13L, CBC, ServiceDetailStatus.PENDING);
        VisitServiceDetail d5 = detail(5L, 14L, CBC, ServiceDetailStatus.IN_PROGRESS);
        // Cùng đợt khám 10 còn có chỉ định siêu âm đang thực hiện bên phòng CĐHA
        VisitServiceDetail imagingSibling = detail(9L, 10L, ULTRASOUND, ServiceDetailStatus.IN_PROGRESS);

        when(detailRepository.findQueue(ServiceCategory.LABORATORY, ServiceDetailStatus.OPEN)).thenReturn(List.of(d1, d2, d3, d4, d5));
        when(visitRepository.findWithPetByIdIn(anyCollection())).thenReturn(List.of(normal, emergency, recalled, postponed, working));
        when(detailHistoryRepository.findByVisitServiceDetailIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(
                history(3L, "RECALL"), history(4L, "POSTPONED")));
        when(detailRepository.findByVisitIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(d1, d2, d3, d4, d5, imagingSibling));

        List<QueueItemResponse> queue = service.queue(ServiceCategory.LABORATORY);

        assertThat(queue).extracting(QueueItemResponse::serviceDetailId).containsExactly(5L, 2L, 3L, 1L, 4L);
        assertThat(queue).extracting(QueueItemResponse::queueState).containsExactly(
                QueueState.IN_PROGRESS, QueueState.WAITING, QueueState.NEXT_IN_LINE, QueueState.WAITING, QueueState.POSTPONED);
        assertThat(queue.get(1).emergency()).isTrue();
        // Huy hiệu liên phòng (FR-36): ca 10 đang được siêu âm bên phòng CĐHA
        assertThat(queue.stream().filter(i -> i.serviceDetailId() == 1L).findFirst().orElseThrow().otherRoomStatus())
                .isEqualTo("IMAGING_IN_PROGRESS");
        assertThat(queue.stream().filter(i -> i.serviceDetailId() == 2L).findFirst().orElseThrow().otherRoomStatus()).isNull();
    }

    @Test
    void queue_ofANonParaclinicalCategory_isRejected() {
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.queue(ServiceCategory.VACCINATION));
    }

    // ================================================================== bắt đầu, tạm hoãn, gọi lại

    @Test
    void start_movesPendingToInProgressAndWritesAudit() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        VisitServiceDetail d = stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);

        ServiceDetailResponse response = service.start(1L);

        assertThat(response.status()).isEqualTo(ServiceDetailStatus.IN_PROGRESS);
        assertThat(d.getStatus()).isEqualTo(ServiceDetailStatus.IN_PROGRESS);
        VisitServiceDetailHistory audit = lastDetailHistory();
        assertThat(audit.getAction()).isEqualTo("START_PROCESSING");
        assertThat(audit.getFromStatus()).isEqualTo(ServiceDetailStatus.PENDING);
        assertThat(audit.getChangedByUserId()).isEqualTo(LAB_TECH);
    }

    @Test
    void start_refusedForAPostponedCase_wrongRoomTechnician_orVisitNoLongerWaiting() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);
        when(detailHistoryRepository.findByVisitServiceDetailIdInOrderByIdAsc(Set.of(1L))).thenReturn(List.of(history(1L, "POSTPONED")));
        assertError(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, () -> service.start(1L));

        // KTV phòng CĐHA không được thao tác chỉ định của phòng Lab
        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.start(1L));

        // Đợt khám đã bị hủy: không thể bắt đầu
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(2L, 101L, CBC, ServiceDetailStatus.PENDING), VisitStatus.CANCELLED);
        assertError(ErrorCode.INVALID_STATUS_TRANSITION, () -> service.start(2L));
    }

    @Test
    void postponeThenRecall_keepsPendingAndOnlyWritesAudit() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        VisitServiceDetail d = stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);

        service.postpone(1L, "Khách vắng sau 3 lần gọi");
        VisitServiceDetailHistory postponed = lastDetailHistory();
        assertThat(postponed.getAction()).isEqualTo("POSTPONED");
        assertThat(postponed.getFromStatus()).isEqualTo(ServiceDetailStatus.PENDING);
        assertThat(postponed.getToStatus()).isEqualTo(ServiceDetailStatus.PENDING);
        assertThat(d.getStatus()).isEqualTo(ServiceDetailStatus.PENDING);   // chưa hao phí vật tư

        when(detailHistoryRepository.findByVisitServiceDetailIdInOrderByIdAsc(Set.of(1L))).thenReturn(List.of(history(1L, "POSTPONED")));
        service.recall(1L);
        assertThat(lastDetailHistory().getAction()).isEqualTo("RECALL");
        assertThat(d.getStatus()).isEqualTo(ServiceDetailStatus.PENDING);
    }

    @Test
    void recall_requiresAPostponedCase_andPostponeCannotRepeat() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);

        assertError(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, () -> service.recall(1L));

        when(detailHistoryRepository.findByVisitServiceDetailIdInOrderByIdAsc(Set.of(1L))).thenReturn(List.of(history(1L, "POSTPONED")));
        assertError(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, () -> service.postpone(1L, null));
    }

    // ================================================================== kết quả xét nghiệm (UC-25)

    @Test
    void submitLab_evaluatesAgainstTheSpeciesRangeAndSnapshotsIt() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        VisitServiceDetail d = stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        LabTestItem wbc = labItem(1L, "WBC");
        LabTestItem plt = labItem(4L, "PLT");
        when(labTestItemRepository.findAllById(List.of(1L, 4L))).thenReturn(List.of(wbc, plt));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 1L)).thenReturn(Optional.of(range(wbc, "6.00", "17.00")));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(4L, 1L)).thenReturn(Optional.of(range(plt, "200.00", "500.00")));
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(1L);

        service.submitLabResults(1L, new LabResultRequest(List.of(
                new LabResultItemRequest(1L, new BigDecimal("21.00"), null),     // Chó: 21 > 17 -> HIGH
                new LabResultItemRequest(4L, new BigDecimal("150.00"), "Tiểu cầu thấp"))));

        ArgumentCaptor<List<LabTestResult>> saved = ArgumentCaptor.forClass(List.class);
        verify(labResultRepository).saveAll(saved.capture());
        LabTestResult highWbc = saved.getValue().get(0);
        assertThat(highWbc.getEvaluation()).isEqualTo(LabEvaluation.HIGH);
        assertThat(highWbc.getMinValueSnapshot()).isEqualByComparingTo("6.00");   // snapshot (BR-24)
        assertThat(highWbc.getMaxValueSnapshot()).isEqualByComparingTo("17.00");
        assertThat(highWbc.getPerformedBy()).isEqualTo(LAB_TECH);
        assertThat(saved.getValue().get(1).getEvaluation()).isEqualTo(LabEvaluation.LOW);
        assertThat(d.getStatus()).isEqualTo(ServiceDetailStatus.COMPLETED);
    }

    @Test
    void submitLab_usesTheRangeOfThePetsOwnSpecies() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        PetVisit catVisit = visit(100L, VisitStatus.WAITING_RESULTS, DOCTOR);
        catVisit.setPet(pet(12L, cat()));
        VisitServiceDetail d = detail(1L, 100L, CBC, ServiceDetailStatus.IN_PROGRESS);
        when(detailRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        when(visitRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(catVisit));
        LabTestItem wbc = labItem(1L, "WBC");
        when(labTestItemRepository.findAllById(List.of(1L))).thenReturn(List.of(wbc));
        // Mèo (species 2): WBC 5.5 - 19.5, nên 18.0 là bình thường (với Chó 6 - 17 thì là cao)
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 2L)).thenReturn(Optional.of(range(wbc, "5.50", "19.50")));
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(1L);

        service.submitLabResults(1L, new LabResultRequest(List.of(new LabResultItemRequest(1L, new BigDecimal("18.00"), null))));

        ArgumentCaptor<List<LabTestResult>> saved = ArgumentCaptor.forClass(List.class);
        verify(labResultRepository).saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getEvaluation()).isEqualTo(LabEvaluation.NORMAL);
        verify(rangeRepository, never()).findByLabTestItemIdAndSpeciesId(1L, 1L);
    }

    @Test
    void submitLab_lastOpenServiceMovesTheVisitToResultsReadyAndNotifiesTheDoctor() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        LabTestItem wbc = labItem(1L, "WBC");
        when(labTestItemRepository.findAllById(List.of(1L))).thenReturn(List.of(wbc));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 1L)).thenReturn(Optional.of(range(wbc, "6.00", "17.00")));
        // Không còn chỉ định Lab / CĐHA nào dở dang
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(0L);

        service.submitLabResults(1L, new LabResultRequest(List.of(new LabResultItemRequest(1L, new BigDecimal("10.00"), null))));

        ArgumentCaptor<com.vetclinic.clinical.entity.PetVisitStatusHistory> history =
                ArgumentCaptor.forClass(com.vetclinic.clinical.entity.PetVisitStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(VisitStatus.WAITING_RESULTS);
        assertThat(history.getValue().getToStatus()).isEqualTo(VisitStatus.RESULTS_READY);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        DoctorPriorityCallbackEvent callback = (DoctorPriorityCallbackEvent) event.getValue();
        assertThat(callback.visitId()).isEqualTo(100L);
        assertThat(callback.doctorId()).isEqualTo(DOCTOR);
    }

    @Test
    void submitLab_whileAnotherServiceIsStillOpen_keepsTheVisitWaiting() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        LabTestItem wbc = labItem(1L, "WBC");
        when(labTestItemRepository.findAllById(List.of(1L))).thenReturn(List.of(wbc));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 1L)).thenReturn(Optional.of(range(wbc, "6.00", "17.00")));
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(1L);

        service.submitLabResults(1L, new LabResultRequest(List.of(new LabResultItemRequest(1L, new BigDecimal("10.00"), null))));

        verify(historyRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void submitLab_rejectsInvalidSubmissions() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        LabResultRequest request = new LabResultRequest(List.of(new LabResultItemRequest(1L, new BigDecimal("10.00"), null)));

        // Chưa bấm "Bắt đầu thực hiện"
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);
        assertError(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, () -> service.submitLabResults(1L, request));

        // Nhập hai lần cùng một chỉ số
        stubLocks(detail(2L, 101L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.submitLabResults(2L, new LabResultRequest(List.of(
                new LabResultItemRequest(1L, new BigDecimal("1.00"), null), new LabResultItemRequest(1L, new BigDecimal("2.00"), null)))));

        // Chỉ số không tồn tại
        stubLocks(detail(3L, 102L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        when(labTestItemRepository.findAllById(List.of(1L))).thenReturn(List.of());
        assertError(ErrorCode.LAB_ITEM_NOT_FOUND, () -> service.submitLabResults(3L, request));

        // Chưa cấu hình khoảng tham chiếu cho loài này
        stubLocks(detail(4L, 103L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        when(labTestItemRepository.findAllById(List.of(1L))).thenReturn(List.of(labItem(1L, "WBC")));
        when(rangeRepository.findByLabTestItemIdAndSpeciesId(1L, 1L)).thenReturn(Optional.empty());
        assertError(ErrorCode.LAB_REFERENCE_NOT_FOUND, () -> service.submitLabResults(4L, request));

        // Chỉ định siêu âm không nhập được như xét nghiệm; và KTV CĐHA không được nhập kết quả Lab
        stubLocks(detail(5L, 104L, ULTRASOUND, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.submitLabResults(5L, request));
        verify(labResultRepository, never()).saveAll(anyList());
    }

    @Test
    void evaluate_boundariesAreInclusive() {
        LabTestReferenceRange range = range(labItem(1L, "WBC"), "6.00", "17.00");
        assertThat(ParaclinicServiceImpl.evaluate(new BigDecimal("6.00"), range)).isEqualTo(LabEvaluation.NORMAL);
        assertThat(ParaclinicServiceImpl.evaluate(new BigDecimal("17.00"), range)).isEqualTo(LabEvaluation.NORMAL);
        assertThat(ParaclinicServiceImpl.evaluate(new BigDecimal("5.99"), range)).isEqualTo(LabEvaluation.LOW);
        assertThat(ParaclinicServiceImpl.evaluate(new BigDecimal("17.01"), range)).isEqualTo(LabEvaluation.HIGH);
    }

    // ================================================================== hủy chỉ định (EX-08)

    @Test
    void cancel_theLastOpenService_movesTheVisitToResultsReady() {
        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        VisitServiceDetail d = stubLocks(detail(1L, 100L, ULTRASOUND, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(0L);

        ServiceDetailResponse response = service.cancel(1L, "  Thú cưng quá hung dữ  ");

        assertThat(response.status()).isEqualTo(ServiceDetailStatus.CANCELLED);
        assertThat(lastDetailHistory().getReason()).isEqualTo("Thú cưng quá hung dữ");
        assertThat(d.getStatus()).isEqualTo(ServiceDetailStatus.CANCELLED);
        verify(publisher).publishEvent(any(DoctorPriorityCallbackEvent.class));
    }

    @Test
    void cancel_whileOtherServicesAreOpen_keepsTheVisitWaiting() {
        loginAs(DOCTOR, RoleCode.VETERINARIAN);   // Bác sĩ đổi hướng xử lý
        stubLocks(detail(1L, 100L, ULTRASOUND, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(1L);

        service.cancel(1L, "Đổi phác đồ");

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cancel_refusedForFinishedServicesAndForTheWrongRoom() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.COMPLETED), VisitStatus.WAITING_RESULTS);
        assertError(ErrorCode.INVALID_SERVICE_DETAIL_STATUS, () -> service.cancel(1L, "x"));

        stubLocks(detail(2L, 101L, ULTRASOUND, ServiceDetailStatus.PENDING), VisitStatus.WAITING_RESULTS);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.cancel(2L, "x"));
    }

    // ================================================================== kết quả CĐHA (UC-26)

    @Test
    void submitImaging_savesResultAndAttachments_thenMovesVisitToResultsReady() {
        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        VisitServiceDetail d = detail(1L, 100L, ULTRASOUND, ServiceDetailStatus.IN_PROGRESS);
        when(detailRepository.findById(1L)).thenReturn(Optional.of(d));
        stubLocks(d, VisitStatus.WAITING_RESULTS);
        when(storage.store(any(), any())).thenReturn(new ImagingStorage.StoredFile("100/a.jpg", "sieu-am.jpg", 1234L, "image/jpeg"));
        when(imagingResultRepository.saveAndFlush(any(ImagingResult.class))).thenAnswer(inv -> {
            ImagingResult r = inv.getArgument(0);
            r.setId(700L);
            return r;
        });
        when(detailRepository.countByVisitAndCategoriesAndStatuses(100L, ServiceCategory.PARACLINICAL, ServiceDetailStatus.OPEN)).thenReturn(0L);

        ServiceDetailResponse response = service.submitImagingResult(1L, "Gan to nhẹ", "Nghi viêm gan",
                List.of(new MockMultipartFile("files", "sieu-am.jpg", "image/jpeg", new byte[]{1})));

        assertThat(response.status()).isEqualTo(ServiceDetailStatus.COMPLETED);
        verify(attachmentRepository).saveAll(anyList());
        verify(publisher).publishEvent(any(DoctorPriorityCallbackEvent.class));
        verify(storage, never()).deleteQuietly(any());
    }

    @Test
    void submitImaging_whenDatabaseWriteFails_deletesTheFilesAlreadyStored() {
        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        when(detailRepository.findById(1L)).thenReturn(Optional.of(detail(1L, 100L, ULTRASOUND, ServiceDetailStatus.IN_PROGRESS)));
        when(storage.store(any(), any())).thenReturn(new ImagingStorage.StoredFile("100/a.jpg", "a.jpg", 10L, "image/jpeg"));
        // Chỉ định bị xóa / không khóa được trong lúc xử lý -> transaction thất bại sau khi tệp đã lưu
        when(detailRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertError(ErrorCode.SERVICE_DETAIL_NOT_FOUND, () -> service.submitImagingResult(1L, "Mô tả", "Kết luận",
                List.of(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1}))));

        verify(storage).deleteQuietly("100/a.jpg");   // không để lại tệp mồ côi
    }

    @Test
    void submitImaging_validatesInputBeforeTouchingTheDiskOrDatabase() {
        MockMultipartFile image = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1});

        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.submitImagingResult(1L, "Mô tả", "Kết luận", List.of(image)));

        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.submitImagingResult(1L, "  ", "Kết luận", List.of(image)));
        assertError(ErrorCode.INVALID_INPUT_DATA, () -> service.submitImagingResult(1L, "Mô tả", null, List.of(image)));
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> service.submitImagingResult(1L, "Mô tả", "Kết luận", List.of()));
        assertError(ErrorCode.INVALID_IMAGING_FILE, () -> service.submitImagingResult(1L, "Mô tả", "Kết luận", null));

        verify(storage, never()).store(any(), any());
        verify(detailRepository, never()).findByIdForUpdate(any());
    }

    // ================================================================== fixtures

    @Test
    void submitLab_rejectsAnItemThatDoesNotBelongToTheOrderedService() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        stubLocks(detail(1L, 100L, CBC, ServiceDetailStatus.IN_PROGRESS), VisitStatus.WAITING_RESULTS);
        when(serviceLabItemRepository.findItemIdsByServiceId(5L)).thenReturn(List.of(1L, 4L));   // CBC chỉ gồm WBC, PLT
        LabTestItem glucose = labItem(9L, "GLU");
        when(labTestItemRepository.findAllById(List.of(9L))).thenReturn(List.of(glucose));

        assertError(ErrorCode.LAB_ITEM_NOT_IN_SERVICE, () -> service.submitLabResults(1L,
                new LabResultRequest(List.of(new LabResultItemRequest(9L, new BigDecimal("5.00"), null)))));
        verify(labResultRepository, never()).saveAll(anyList());
    }

    @Test
    void labForm_listsOnlyTheServicesItemsWithTheRangeOfThePetsSpecies() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        VisitServiceDetail d = detail(1L, 100L, CBC, ServiceDetailStatus.PENDING);
        when(detailRepository.findById(1L)).thenReturn(Optional.of(d));
        when(visitRepository.findWithPetById(100L)).thenReturn(Optional.of(visit(100L, VisitStatus.WAITING_RESULTS, DOCTOR)));
        LabTestItem wbc = labItem(1L, "WBC");
        LabTestItem glucose = labItem(9L, "GLU");
        when(serviceLabItemRepository.findItemIdsByServiceId(5L)).thenReturn(List.of(1L));
        when(labTestItemRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(glucose, wbc));
        LabTestReferenceRange dogRange = range(wbc, "6.00", "17.00");
        LabTestReferenceRange catRange = LabTestReferenceRange.builder().labTestItem(wbc).species(cat())
                .minValue(new BigDecimal("5.50")).maxValue(new BigDecimal("19.50")).build();
        when(rangeRepository.findByLabTestItemIdIn(List.of(1L))).thenReturn(List.of(dogRange, catRange));

        var form = service.labForm(1L);

        assertThat(form).extracting(i -> i.code()).containsExactly("WBC");
        assertThat(form.get(0).ranges()).hasSize(1);
        assertThat(form.get(0).ranges().get(0).minValue()).isEqualByComparingTo("6.00");   // visit() là thú cưng loài Chó
    }

    @Test
    void labForm_withoutConfigurationShowsEveryItem_andIsForTheLabOnly() {
        loginAs(LAB_TECH, RoleCode.LAB_TECHNICIAN);
        when(detailRepository.findById(1L)).thenReturn(Optional.of(detail(1L, 100L, CBC, ServiceDetailStatus.PENDING)));
        when(visitRepository.findWithPetById(100L)).thenReturn(Optional.of(visit(100L, VisitStatus.WAITING_RESULTS, DOCTOR)));
        when(labTestItemRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(labItem(1L, "WBC"), labItem(4L, "PLT")));

        assertThat(service.labForm(1L)).hasSize(2);

        loginAs(IMAGING_TECH, RoleCode.IMAGING_TECHNICIAN);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.labForm(1L));
    }

    /** Khóa chỉ định rồi khóa đợt khám, đúng thứ tự mà dịch vụ thật dùng. */
    private VisitServiceDetail stubLocks(VisitServiceDetail detail, VisitStatus visitStatus) {
        PetVisit visit = visit(detail.getVisitId(), visitStatus, DOCTOR);
        when(detailRepository.findByIdForUpdate(detail.getId())).thenReturn(Optional.of(detail));
        when(visitRepository.findByIdForUpdate(detail.getVisitId())).thenReturn(Optional.of(visit));
        return detail;
    }

    private static LabTestItem labItem(long id, String code) {
        return LabTestItem.builder().id(id).code(code).name(code).unit("10^9/L").build();
    }

    private static LabTestReferenceRange range(LabTestItem item, String min, String max) {
        return LabTestReferenceRange.builder().labTestItem(item).species(dog())
                .minValue(new BigDecimal(min)).maxValue(new BigDecimal(max)).build();
    }

    private static VisitServiceDetailHistory history(long detailId, String action) {
        return VisitServiceDetailHistory.builder().visitServiceDetailId(detailId).action(action)
                .fromStatus(ServiceDetailStatus.PENDING).toStatus(ServiceDetailStatus.PENDING).changedByUserId(LAB_TECH).build();
    }

    private VisitServiceDetailHistory lastDetailHistory() {
        ArgumentCaptor<VisitServiceDetailHistory> captor = ArgumentCaptor.forClass(VisitServiceDetailHistory.class);
        verify(detailHistoryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
