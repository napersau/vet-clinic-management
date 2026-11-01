package com.vetclinic.billing.repository;

import com.vetclinic.billing.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Page<InventoryTransaction> findByMedicineIdOrderByIdDesc(Long medicineId, Pageable pageable);
}
