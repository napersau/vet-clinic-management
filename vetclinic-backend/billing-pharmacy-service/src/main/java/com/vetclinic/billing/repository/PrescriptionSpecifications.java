package com.vetclinic.billing.repository;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.Prescription;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PrescriptionSpecifications {

    private PrescriptionSpecifications() {
    }

    /**
     * awaitingDispense = đơn PRESCRIBED mà hóa đơn liên kết đã PAID: danh sách "đơn thuốc chờ phát" của quầy (UC-36).
     * Tham số null / false thì bỏ qua điều kiện tương ứng.
     */
    public static Specification<Prescription> filter(PrescriptionStatus status, Long visitId, boolean awaitingDispense) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (visitId != null) {
                predicates.add(cb.equal(root.get("visitId"), visitId));
            }
            if (awaitingDispense) {
                predicates.add(cb.equal(root.get("status"), PrescriptionStatus.PRESCRIBED));
                Subquery<Long> paid = query.subquery(Long.class);
                Root<Invoice> invoice = paid.from(Invoice.class);
                paid.select(invoice.get("id")).where(
                        cb.equal(invoice.get("prescriptionId"), root.get("id")),
                        cb.equal(invoice.get("paymentStatus"), PaymentStatus.PAID));
                predicates.add(cb.exists(paid));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
