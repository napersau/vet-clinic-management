package com.vetclinic.billing.repository;

import com.vetclinic.billing.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long>, JpaSpecificationExecutor<Medicine> {

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Trừ kho nguyên tử (quy chuẩn §8.1): điều kiện tồn kho nằm ngay trong câu UPDATE nên hai giao dịch đồng thời không
     * thể cùng trừ vượt số còn lại. Trả 0 dòng = không đủ hàng (hoặc thuốc không tồn tại).
     *
     * Cố ý KHÔNG clearAutomatically: nó sẽ tách rời đơn thuốc đang xử lý khỏi persistence context. Thay vào đó nơi gọi
     * dùng EntityManager.refresh(medicine) để đọc lại tồn kho thật, không bao giờ tin giá trị cũ trong entity.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Medicine m set m.stockQuantity = m.stockQuantity - :quantity "
            + "where m.id = :id and m.stockQuantity >= :quantity")
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Nhập kho / cân chỉnh (delta có thể âm). Không cho tồn kho âm: trả 0 dòng nếu sẽ âm hoặc thuốc không tồn tại. */
    @Modifying(flushAutomatically = true)
    @Query("update Medicine m set m.stockQuantity = m.stockQuantity + :delta "
            + "where m.id = :id and m.stockQuantity + :delta >= 0")
    int addStock(@Param("id") Long id, @Param("delta") int delta);
}
