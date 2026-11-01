package com.vetclinic.billing.repository;

import com.vetclinic.billing.entity.Prescription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long>, JpaSpecificationExecutor<Prescription> {

    /** Khóa dòng khi xuất thuốc / hủy đơn: hai lần bấm đồng thời chỉ một lần thắng (EX-16). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescription p where p.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") Long id);

    // open-in-view đã tắt nên luôn nạp kèm dòng thuốc và thuốc
    @EntityGraph(attributePaths = {"items", "items.medicine"})
    Optional<Prescription> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.medicine"})
    Optional<Prescription> findWithItemsByVisitId(Long visitId);

    /** Cho tra cứu bệnh án: đơn của nhiều đợt khám cùng lúc (không gồm đơn đã hủy). */
    @EntityGraph(attributePaths = {"items", "items.medicine"})
    java.util.List<Prescription> findWithItemsByVisitIdInAndStatusNot(java.util.Collection<Long> visitIds,
                                                                      com.vetclinic.billing.constant.PrescriptionStatus status);
}
