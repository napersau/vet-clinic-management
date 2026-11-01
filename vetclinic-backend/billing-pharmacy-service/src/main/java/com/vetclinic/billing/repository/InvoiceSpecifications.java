package com.vetclinic.billing.repository;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.entity.Invoice;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    /** createdFrom bao gồm, createdTo loại trừ. Tham số null thì bỏ qua điều kiện tương ứng. */
    public static Specification<Invoice> filter(PaymentStatus status, String code, Long visitId,
                                                Instant createdFrom, Instant createdTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), status));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("invoiceCode")), code.trim().toUpperCase(Locale.ROOT)));
            }
            if (visitId != null) {
                predicates.add(cb.equal(root.get("visitId"), visitId));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThan(root.<Instant>get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
