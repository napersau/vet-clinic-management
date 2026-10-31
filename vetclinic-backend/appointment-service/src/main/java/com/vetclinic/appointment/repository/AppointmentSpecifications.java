package com.vetclinic.appointment.repository;

import com.vetclinic.appointment.constant.AppointmentStatus;
import com.vetclinic.appointment.entity.Appointment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    /** Tham số null thì bỏ qua điều kiện tương ứng. */
    public static Specification<Appointment> filter(Long ownerId, Long petId, String code,
                                                    LocalDate fromDate, LocalDate toDate, AppointmentStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ownerId != null) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (petId != null) {
                predicates.add(cb.equal(root.get("petId"), petId));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("appointmentCode")), code.trim().toUpperCase(Locale.ROOT)));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("appointmentDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("appointmentDate"), toDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
