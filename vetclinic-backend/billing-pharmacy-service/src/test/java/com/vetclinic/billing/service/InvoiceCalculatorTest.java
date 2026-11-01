package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.InvoiceItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCalculatorTest {

    private static Invoice invoiceWithDiscount(String discount) {
        return Invoice.builder().totalServiceAmount(BigDecimal.ZERO).totalMedicineAmount(BigDecimal.ZERO)
                .totalVatAmount(BigDecimal.ZERO).discountAmount(new BigDecimal(discount)).finalAmount(BigDecimal.ZERO).build();
    }

    @Test
    void buildItem_computesSubtotalAndVat() {
        InvoiceItem item = InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 1L, "Xét nghiệm máu", 3,
                new BigDecimal("12.5"), new BigDecimal("0.10"));

        assertThat(item.getSubtotal()).isEqualByComparingTo("37.50");
        assertThat(item.getVatAmount()).isEqualByComparingTo("3.75");
        assertThat(item.getVatRate()).isEqualByComparingTo("0.10");
        assertThat(item.getUnitPrice().scale()).isEqualTo(2);
    }

    @Test
    void buildItem_roundsVatHalfUpToTwoDecimals() {
        // 33.33 * 0.05 = 1.6665 -> 1.67 (HALF_UP), không phải 1.66
        InvoiceItem item = InvoiceCalculator.buildItem(InvoiceItemType.MEDICINE, 1L, "Thuốc", 1,
                new BigDecimal("33.33"), new BigDecimal("0.05"));

        assertThat(item.getVatAmount()).isEqualByComparingTo("1.67");
    }

    @Test
    void buildItem_truncatesOverlongNameToColumnLength() {
        InvoiceItem item = InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 1L, "x".repeat(300), 1,
                BigDecimal.TEN, BigDecimal.ZERO);

        assertThat(item.getItemName()).hasSize(150);
    }

    @Test
    void recalculate_separatesServiceFromMedicineAndAppliesDiscount() {
        Invoice invoice = invoiceWithDiscount("20000");
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 1L, "Khám", 1, new BigDecimal("100000"), new BigDecimal("0.10")));
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.SERVICE, 2L, "CBC", 1, new BigDecimal("150000"), new BigDecimal("0.10")));
        invoice.addItem(InvoiceCalculator.buildItem(InvoiceItemType.MEDICINE, 3L, "Clavamox", 3, new BigDecimal("15000"), new BigDecimal("0.05")));

        InvoiceCalculator.recalculate(invoice);

        assertThat(invoice.getTotalServiceAmount()).isEqualByComparingTo("250000");
        assertThat(invoice.getTotalMedicineAmount()).isEqualByComparingTo("45000");
        assertThat(invoice.getTotalVatAmount()).isEqualByComparingTo("27250");      // 25000 + 2250
        assertThat(InvoiceCalculator.gross(invoice)).isEqualByComparingTo("322250");
        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("302250");        // - 20000 chiết khấu
    }

    @Test
    void recalculate_withNoItemsIsZero() {
        Invoice invoice = invoiceWithDiscount("0");

        InvoiceCalculator.recalculate(invoice);

        assertThat(invoice.getFinalAmount()).isEqualByComparingTo("0");
    }
}
