package com.vetclinic.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;

/**
 * Danh mục thuốc và tồn kho.
 *
 * Tồn kho CHỈ được đổi bằng câu UPDATE nguyên tử trong MedicineRepository (deductStock / addStock). Không bao giờ gán
 * stockQuantity rồi save(): @DynamicUpdate bảo đảm khi Admin sửa tên / giá, Hibernate chỉ ghi các cột đã đổi, nên không
 * thể ghi đè số tồn kho vừa được một giao dịch xuất thuốc cập nhật. @Version chỉ bảo vệ các sửa đổi danh mục.
 */
@Entity
@Table(name = "medicines")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Medicine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "active_ingredient", length = 150)
    private String activeIngredient;

    @Column(name = "dosage_form", nullable = false, length = 50)
    private String dosageForm;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "min_stock_alert", nullable = false)
    @Builder.Default
    private int minStockAlert = 10;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
