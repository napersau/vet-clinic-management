package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.PetVisitStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PetVisitStatusHistoryRepository extends JpaRepository<PetVisitStatusHistory, Long> {
    java.util.List<PetVisitStatusHistory> findByVisitIdOrderByIdAsc(Long visitId);
}
