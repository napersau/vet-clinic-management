package com.vetclinic.clinical.repository;

import com.vetclinic.clinical.entity.ServiceLabTestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceLabTestItemRepository extends JpaRepository<ServiceLabTestItem, ServiceLabTestItem.Key> {

    @Query("select s.labTestItemId from ServiceLabTestItem s where s.serviceId = :serviceId")
    List<Long> findItemIdsByServiceId(@Param("serviceId") Long serviceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ServiceLabTestItem s where s.serviceId = :serviceId")
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
