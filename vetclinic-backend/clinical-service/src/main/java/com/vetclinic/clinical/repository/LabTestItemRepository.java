package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.LabTestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabTestItemRepository extends JpaRepository<LabTestItem, Long> {
    java.util.List<LabTestItem> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);
}
