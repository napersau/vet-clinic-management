package com.vetclinic.billing.repository;

import com.vetclinic.billing.entity.Invoice;
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
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    /** Khóa dòng cho mọi thao tác ghi lên hóa đơn: thanh toán hai lần, chiết khấu chen ngang thanh toán... */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.visitId = :visitId")
    Optional<Invoice> findByVisitIdForUpdate(@Param("visitId") Long visitId);

    @EntityGraph(attributePaths = "items")
    Optional<Invoice> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    Optional<Invoice> findWithItemsByVisitId(Long visitId);

    Optional<Invoice> findByVisitId(Long visitId);

    Optional<Invoice> findByPrescriptionId(Long prescriptionId);

    boolean existsByVisitId(Long visitId);

    boolean existsByVisitIdAndPaymentStatusNot(Long visitId, com.vetclinic.billing.constant.PaymentStatus status);

    /**
     * Còn hóa đơn "sống" (PENDING hoặc PAID) cho đợt khám. Hóa đơn đã hủy (đợt khám được mở lại để điều chỉnh) không chặn
     * việc sửa đơn thuốc nữa.
     */
    default boolean hasActiveInvoice(Long visitId) {
        return existsByVisitIdAndPaymentStatusNot(visitId, com.vetclinic.billing.constant.PaymentStatus.CANCELLED);
    }

    /** Hóa đơn PAID có ngày thu tiền trong [start, end): nguồn của báo cáo doanh thu. */
    @Query("select new com.vetclinic.billing.repository.RevenueRow(i.paidAt, i.totalServiceAmount, i.totalMedicineAmount, "
            + "i.totalVatAmount, i.discountAmount, i.finalAmount) from Invoice i "
            + "where i.paymentStatus = com.vetclinic.billing.constant.PaymentStatus.PAID "
            + "and i.paidAt >= :start and i.paidAt < :end")
    java.util.List<RevenueRow> findPaidRevenueRows(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);

    /** Số hóa đơn và tiền theo trạng thái cho các hóa đơn lập trong [start, end). */
    @Query("select new com.vetclinic.billing.repository.StatusRow(i.paymentStatus, count(i), sum(i.finalAmount)) "
            + "from Invoice i where i.createdAt >= :start and i.createdAt < :end group by i.paymentStatus")
    java.util.List<StatusRow> summarizeByStatus(@Param("start") java.time.Instant start, @Param("end") java.time.Instant end);
}
