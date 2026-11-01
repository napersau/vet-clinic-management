package com.vetclinic.billing.repository;

import com.vetclinic.billing.entity.Medicine;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MedicineSpecifications {

    private MedicineSpecifications() {
    }

    /** Tham số null / false thì bỏ qua điều kiện tương ứng. keyword tìm theo mã, tên hoặc hoạt chất. */
    public static Specification<Medicine> filter(String keyword, Boolean active, boolean lowStockOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + escape(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like, '\\'),
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("activeIngredient")), like, '\\')));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (lowStockOnly) {
                predicates.add(cb.lessThanOrEqualTo(root.<Integer>get("stockQuantity"), root.<Integer>get("minStockAlert")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
