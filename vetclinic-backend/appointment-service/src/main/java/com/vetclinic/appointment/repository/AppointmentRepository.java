package com.vetclinic.appointment.repository;

import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.dto.internal.SlotBookedCount;
import com.vetclinic.appointment.entity.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /**
     * Khóa dòng (SELECT ... FOR UPDATE) cho mọi thao tác chuyển trạng thái. Bảng appointments không có cột version,
     * nên đây là chốt chặn để confirm / cancel / check-in / no-show xảy ra cùng lúc không ghi đè lên nhau.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    long countByAppointmentDateAndStartTimeAndStatusIn(LocalDate date, LocalTime startTime,
                                                        Collection<AppointmentStatus> statuses);

    @Query("""
            select new com.vetclinic.appointment.dto.internal.SlotBookedCount(a.startTime, count(a))
            from Appointment a
            where a.appointmentDate = :date and a.status in :statuses
            group by a.startTime
            """)
    List<SlotBookedCount> countBookedBySlot(@Param("date") LocalDate date,
                                            @Param("statuses") Collection<AppointmentStatus> statuses);

    boolean existsByPetIdAndAppointmentDateAndStatusIn(Long petId, LocalDate date,
                                                       Collection<AppointmentStatus> statuses);

    boolean existsByPetIdAndAppointmentDateAndStatusInAndIdNot(Long petId, LocalDate date,
                                                               Collection<AppointmentStatus> statuses, Long id);

    /**
     * Lịch hẹn CONFIRMED đã quá giờ kết thúc khung hẹn + thời gian chờ. So sánh theo (ngày, giờ) nên lịch của các
     * ngày trước cũng được quét, kể cả khi service đã tắt lúc nửa đêm hay cuối tuần.
     */
    @Query("""
            select a.id from Appointment a
            where a.status = :status
              and (a.appointmentDate < :cutoffDate
                   or (a.appointmentDate = :cutoffDate and a.endTime < :cutoffTime))
            order by a.id
            """)
    List<Long> findIdsEndedBefore(@Param("status") AppointmentStatus status,
                                  @Param("cutoffDate") LocalDate cutoffDate,
                                  @Param("cutoffTime") LocalTime cutoffTime);

    /** Báo cáo lịch hẹn: số lịch theo trạng thái / kênh đặt, ngày hẹn trong [from, to]. */
    @Query("select a.status, count(a) from Appointment a where a.appointmentDate >= :from and a.appointmentDate <= :to group by a.status")
    List<Object[]> countByStatusBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select a.bookingChannel, count(a) from Appointment a where a.appointmentDate >= :from and a.appointmentDate <= :to group by a.bookingChannel")
    List<Object[]> countByChannelBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
