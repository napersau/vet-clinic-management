package com.vetclinic.billing.entity;

import com.vetclinic.billing.constant.InventoryTransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Nhật ký biến động kho (audit). quantity là số lượng của giao dịch: IMPORT và EXPORT_PRESCRIPTION luôn dương (chiều tăng
 * giảm do transactionType quyết định), riêng ADJUSTMENT mang dấu (+ thừa, - hao hụt). balanceAfter là tồn kho ngay sau giao dịch.
 */
@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private InventoryTransactionType transactionType;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    /** Với EXPORT_PRESCRIPTION: id đơn thuốc. */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
