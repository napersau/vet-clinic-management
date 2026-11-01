package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.ClinicService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicServiceRepository extends JpaRepository<ClinicService, Long> {
    boolean existsByCode(String code);

    java.util.Optional<ClinicService> findByCode(String code);

    java.util.List<ClinicService> findByActiveTrueOrderByCategoryAscNameAsc();

    java.util.List<ClinicService> findAllByOrderByCategoryAscNameAsc();

    java.util.List<ClinicService> findByIdInAndActiveTrue(java.util.Collection<Long> ids);
}
