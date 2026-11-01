package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.entity.PetVisit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PetVisitRepository extends JpaRepository<PetVisit, Long>, JpaSpecificationExecutor<PetVisit> {

    /**
     * Khóa dòng (SELECT ... FOR UPDATE) cho mọi thao tác ghi lên đợt khám: serialize các thao tác cùng lúc
     * (hai KTV cùng hoàn tất, Bác sĩ bấm hai lần...) và đảm bảo kiểm tra trạng thái nguồn không bị chen ngang.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PetVisit v where v.id = :id")
    Optional<PetVisit> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    Optional<PetVisit> findWithPetById(Long id);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    List<PetVisit> findWithPetByIdIn(Collection<Long> ids);

    /**
     * Hàng đợi sảnh (nghiệp vụ §8.4 / §9): 1) cấp cứu tuyệt đối; 2) có hẹn đúng khung giờ, theo giờ hẹn; 3) vãng lai và khách
     * hẹn nhưng đến trễ, theo giờ check-in (FIFO). Trong mỗi nhóm cùng mốc thì theo giờ check-in rồi id.
     */
    @EntityGraph(attributePaths = {"pet", "pet.species"})
    @Query("select v from PetVisit v where v.status = :status order by v.emergency desc, "
            + "case when v.scheduledAt is null then 1 else 0 end asc, v.scheduledAt asc, v.checkedInAt asc, v.id asc")
    List<PetVisit> findQueueByStatus(@Param("status") VisitStatus status);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    List<PetVisit> findByDoctorIdAndStatusInOrderByCheckedInAtAscIdAsc(Long doctorId, Collection<VisitStatus> statuses);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    List<PetVisit> findByPetIdOrderByIdDesc(Long petId);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    org.springframework.data.domain.Page<PetVisit> findByPetId(Long petId, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"pet", "pet.species"})
    org.springframework.data.domain.Page<PetVisit> findByPetIdAndStatus(Long petId, VisitStatus status,
                                                                        org.springframework.data.domain.Pageable pageable);

    /** Báo cáo vận hành: số ca theo trạng thái, ngày check-in trong [start, end). */
    @Query("select v.status, count(v) from PetVisit v where v.checkedInAt >= :start and v.checkedInAt < :end group by v.status")
    List<Object[]> countByStatusBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("select count(v) from PetVisit v where v.emergency = true and v.checkedInAt >= :start and v.checkedInAt < :end")
    long countEmergencyBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    @Query("select v.doctorId, v.status, count(v) from PetVisit v where v.doctorId is not null "
            + "and v.checkedInAt >= :start and v.checkedInAt < :end group by v.doctorId, v.status")
    List<Object[]> countByDoctorAndStatusBetween(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    boolean existsByPetIdAndStatusIn(Long petId, Collection<VisitStatus> statuses);

    boolean existsByAppointmentId(Long appointmentId);

    boolean existsByDoctorIdAndStatus(Long doctorId, VisitStatus status);

    boolean existsByDoctorIdAndStatusAndCallbackPostponedAtIsNull(Long doctorId, VisitStatus status);

    // ---- Trạng thái Bác sĩ (tính động): chỉ cần biết bác sĩ nào đang ở tình trạng nào
    @Query("select distinct v.doctorId from PetVisit v where v.doctorId is not null and v.status in :statuses")
    Set<Long> findDoctorIdsWithStatusIn(@Param("statuses") Collection<VisitStatus> statuses);

    @Query("select distinct v.doctorId from PetVisit v "
            + "where v.doctorId is not null and v.status = :status and v.callbackPostponedAt is null")
    Set<Long> findDoctorIdsWithUnpostponedStatus(@Param("status") VisitStatus status);
}
