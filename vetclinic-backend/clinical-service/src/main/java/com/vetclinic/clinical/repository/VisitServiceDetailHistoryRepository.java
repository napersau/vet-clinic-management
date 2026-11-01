package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.VisitServiceDetailHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitServiceDetailHistoryRepository extends JpaRepository<VisitServiceDetailHistory, Long> {
    java.util.List<VisitServiceDetailHistory> findByVisitServiceDetailIdInOrderByIdAsc(java.util.Collection<Long> detailIds);
}
