package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VisitServiceDetailRepository extends JpaRepository<VisitServiceDetail, Long> {

    /** Khóa dòng chỉ định. KHÔNG fetch join dịch vụ: tránh khóa lây sang bảng danh mục services dùng chung. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from VisitServiceDetail d where d.id = :id")
    Optional<VisitServiceDetail> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "service")
    List<VisitServiceDetail> findByVisitIdOrderByIdAsc(Long visitId);

    @EntityGraph(attributePaths = "service")
    List<VisitServiceDetail> findByVisitIdInOrderByIdAsc(Collection<Long> visitIds);

    @EntityGraph(attributePaths = "service")
    List<VisitServiceDetail> findByVisitIdAndStatusOrderByIdAsc(Long visitId, ServiceDetailStatus status);

    @EntityGraph(attributePaths = "service")
    @Query("select d from VisitServiceDetail d where d.service.category = :category and d.status in :statuses "
            + "order by d.createdAt asc, d.id asc")
    List<VisitServiceDetail> findQueue(@Param("category") ServiceCategory category,
                                       @Param("statuses") Collection<ServiceDetailStatus> statuses);

    @Query("select count(d) from VisitServiceDetail d where d.visitId = :visitId "
            + "and d.service.category in :categories and d.status in :statuses")
    long countByVisitAndCategoriesAndStatuses(@Param("visitId") Long visitId,
                                              @Param("categories") Collection<ServiceCategory> categories,
                                              @Param("statuses") Collection<ServiceDetailStatus> statuses);

    @Query("select count(d) > 0 from VisitServiceDetail d where d.visitId = :visitId and d.service.code = :code")
    boolean existsByVisitAndServiceCode(@Param("visitId") Long visitId, @Param("code") String code);

    /** Báo cáo vận hành: số đợt khám (check-in trong [start, end)) có ít nhất một chỉ định thuộc nhóm đã chọn và chưa bị hủy. */
    @Query("select count(distinct d.visitId) from VisitServiceDetail d, PetVisit v where v.id = d.visitId "
            + "and d.service.category in :categories and d.status <> com.vetclinic.clinical.constant.ServiceDetailStatus.CANCELLED "
            + "and v.checkedInAt >= :start and v.checkedInAt < :end")
    long countVisitsWithCategoriesBetween(@Param("categories") Collection<com.vetclinic.clinical.constant.ServiceCategory> categories,
                                          @Param("start") java.time.Instant start, @Param("end") java.time.Instant end);
}
