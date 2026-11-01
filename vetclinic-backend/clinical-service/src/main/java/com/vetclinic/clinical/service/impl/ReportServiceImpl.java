package com.vetclinic.clinical.service.impl;

import com.vetclinic.clinical.client.AuthClient;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.dto.response.OperationsReportResponse;
import com.vetclinic.clinical.dto.response.UserSummaryResponse;
import com.vetclinic.clinical.repository.PetVisitRepository;
import com.vetclinic.clinical.repository.VisitServiceDetailRepository;
import com.vetclinic.clinical.service.ReportService;
import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Phần đọc CSDL chạy trong transaction chỉ-đọc; tên bác sĩ lấy từ auth-service NGOÀI transaction (lỗi thì để null). */
@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_DAYS = 30;
    private static final long MAX_DAYS = 366;

    private final PetVisitRepository visitRepository;
    private final VisitServiceDetailRepository detailRepository;
    private final AuthClient authClient;
    private final Clock clock;
    private final TransactionTemplate readTx;

    public ReportServiceImpl(PetVisitRepository visitRepository, VisitServiceDetailRepository detailRepository,
                             AuthClient authClient, Clock clock, PlatformTransactionManager transactionManager) {
        this.visitRepository = visitRepository;
        this.detailRepository = detailRepository;
        this.authClient = authClient;
        this.clock = clock;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    private record Raw(Map<VisitStatus, Long> byStatus, long emergency, long paraclinical,
                       Map<Long, Map<VisitStatus, Long>> byDoctor) {
    }

    @Override
    public OperationsReportResponse operations(LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate != null ? toDate : LocalDate.now(clock);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_INPUT_DATA, "Khoảng báo cáo tối đa " + MAX_DAYS + " ngày");
        }
        Instant start = from.atStartOfDay(clock.getZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();

        Raw raw = readTx.execute(status -> {
            Map<VisitStatus, Long> byStatus = new EnumMap<>(VisitStatus.class);
            for (Object[] row : visitRepository.countByStatusBetween(start, end)) {
                byStatus.put((VisitStatus) row[0], (Long) row[1]);
            }
            Map<Long, Map<VisitStatus, Long>> byDoctor = new HashMap<>();
            for (Object[] row : visitRepository.countByDoctorAndStatusBetween(start, end)) {
                byDoctor.computeIfAbsent((Long) row[0], k -> new EnumMap<>(VisitStatus.class)).put((VisitStatus) row[1], (Long) row[2]);
            }
            return new Raw(byStatus, visitRepository.countEmergencyBetween(start, end),
                    detailRepository.countVisitsWithCategoriesBetween(ServiceCategory.PARACLINICAL, start, end), byDoctor);
        });

        long total = raw.byStatus().values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (VisitStatus s : VisitStatus.values()) {
            byStatus.put(s.name(), raw.byStatus().getOrDefault(s, 0L));
        }
        Map<Long, String> names = doctorNames(raw.byDoctor().keySet());
        List<OperationsReportResponse.DoctorLoad> doctors = new TreeMap<>(raw.byDoctor()).entrySet().stream()
                .map(e -> new OperationsReportResponse.DoctorLoad(e.getKey(), names.get(e.getKey()),
                        e.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        e.getValue().getOrDefault(VisitStatus.COMPLETED, 0L)))
                .sorted(Comparator.comparingLong(OperationsReportResponse.DoctorLoad::assignedVisits).reversed()).toList();

        return new OperationsReportResponse(from, to, total, byStatus.get("COMPLETED"), byStatus.get("CANCELLED"),
                raw.emergency(), total - raw.paraclinical(), raw.paraclinical(), byStatus, doctors);
    }

    private Map<Long, String> doctorNames(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<UserSummaryResponse>> response = authClient.getUsers(List.copyOf(ids));
            Map<Long, String> names = new HashMap<>();
            if (response != null && response.getData() != null) {
                response.getData().forEach(u -> names.put(u.id(), u.fullName()));
            }
            return names;
        } catch (RuntimeException e) {
            log.warn("Không lấy được tên bác sĩ từ auth-service: {}", e.getMessage());
            return Map.of();
        }
    }
}
