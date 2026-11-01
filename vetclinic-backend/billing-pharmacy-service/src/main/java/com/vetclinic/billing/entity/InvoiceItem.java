package com.vetclinic.billing.entity;

import com.vetclinic.billing.constant.InvoiceItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private InvoiceItemType itemType;

    /** services.id (clinical) hoặc medicines.id tùy itemType. */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    /** Snapshot tên tại thời điểm lập hóa đơn. */
    @Column(name = "item_name", nullable = false, length = 150)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
