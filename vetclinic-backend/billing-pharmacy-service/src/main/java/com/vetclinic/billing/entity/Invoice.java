package com.vetclinic.billing.entity;

import com.vetclinic.billing.constant.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_code", nullable = false, unique = true, length = 50)
    private String invoiceCode;

    /** Khóa ngoại logic -> pet_clinic_clinical_db.pet_visits(id); UNIQUE nên tạo hóa đơn từ sự kiện là idempotent (BR-19). */
    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    /** Khóa ngoại vật lý nội bộ; NULL nếu ca không kê đơn hoặc đơn đã hủy trước khi thanh toán. */
    @Column(name = "prescription_id", unique = true)
    private Long prescriptionId;

    /** NULL khi hóa đơn PENDING tự tạo từ sự kiện; gán lúc xác nhận thanh toán. */
    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "total_service_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalServiceAmount;

    @Column(name = "total_medicine_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalMedicineAmount;

    @Column(name = "total_vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalVatAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        items.add(item);
    }
}
