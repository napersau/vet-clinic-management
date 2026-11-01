package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.ServiceDetailStatus;
import com.vetclinic.clinical.entity.VisitServiceDetail;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Chỉ định dịch vụ trong đợt khám. Các truy vấn hàng đợi Lab / CĐHA và khóa dòng chỉ định (NV3), dịch vụ tính tiền (NV4a),
 * bệnh án / báo cáo (NV5) thêm khi chuyển phần đó.
 */
@Repository
public interface VisitServiceDetailRepository extends JpaRepository<VisitServiceDetail, Long> {

    @EntityGraph(attributePaths = "service")
    List<VisitServiceDetail> findByVisitIdOrderByIdAsc(Long visitId);

    @Query("select count(d) from VisitServiceDetail d where d.visitId = :visitId "
            + "and d.service.category in :categories and d.status in :statuses")
    long countByVisitAndCategoriesAndStatuses(@Param("visitId") Long visitId,
                                              @Param("categories") Collection<ServiceCategory> categories,
                                              @Param("statuses") Collection<ServiceDetailStatus> statuses);

    @Query("select count(d) > 0 from VisitServiceDetail d where d.visitId = :visitId and d.service.code = :code")
    boolean existsByVisitAndServiceCode(@Param("visitId") Long visitId, @Param("code") String code);
}
