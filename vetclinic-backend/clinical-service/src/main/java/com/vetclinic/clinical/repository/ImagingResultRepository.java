package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.ImagingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImagingResultRepository extends JpaRepository<ImagingResult, Long> {
    java.util.List<ImagingResult> findByVisitServiceDetailIdIn(java.util.Collection<Long> detailIds);
}
