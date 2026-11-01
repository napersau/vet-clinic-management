package com.vetclinic.billing.repository;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.entity.Prescription;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class PrescriptionSpecifications {

    private PrescriptionSpecifications() {
    }

    /**
     * Lọc theo trạng thái và đợt khám; tham số null thì bỏ qua. Bộ lọc awaitingDispense (đơn PRESCRIBED có hóa đơn đã PAID:
     * hàng đợi phát thuốc của quầy, UC-36) thêm ở NV4b cùng hóa đơn.
     */
    public static Specification<Prescription> filter(PrescriptionStatus status, Long visitId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (visitId != null) {
                predicates.add(cb.equal(root.get("visitId"), visitId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
