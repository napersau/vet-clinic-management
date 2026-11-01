package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.InvoiceItemType;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.InvoiceItem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Toàn bộ phép tính tiền của hóa đơn nằm ở đây (BigDecimal, làm tròn HALF_UP 2 chữ số, CLAUDE.md §5.8):
 * subtotal = quantity * unitPrice, vat = subtotal * rate, final = Σsubtotal + Σvat - discount.
 */
public final class InvoiceCalculator {

    private InvoiceCalculator() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static InvoiceItem buildItem(InvoiceItemType type, Long referenceId, String name, int quantity,
                                        BigDecimal unitPrice, BigDecimal vatRate) {
        BigDecimal subtotal = money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal rate = vatRate.setScale(2, RoundingMode.HALF_UP);
        return InvoiceItem.builder()
                .itemType(type)
                .referenceId(referenceId)
                .itemName(name.length() > 150 ? name.substring(0, 150) : name)
                .quantity(quantity)
                .unitPrice(money(unitPrice))
                .vatRate(rate)
                .vatAmount(money(subtotal.multiply(rate)))
                .subtotal(subtotal)
                .build();
    }

    /** Tính lại ba tổng và final_amount từ các dòng hiện có, giữ nguyên chiết khấu. */
    public static void recalculate(Invoice invoice) {
        BigDecimal service = BigDecimal.ZERO;
        BigDecimal medicine = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (InvoiceItem item : invoice.getItems()) {
            if (item.getItemType() == InvoiceItemType.SERVICE) {
                service = service.add(item.getSubtotal());
            } else {
                medicine = medicine.add(item.getSubtotal());
            }
            vat = vat.add(item.getVatAmount());
        }
        invoice.setTotalServiceAmount(money(service));
        invoice.setTotalMedicineAmount(money(medicine));
        invoice.setTotalVatAmount(money(vat));
        invoice.setFinalAmount(money(gross(invoice).subtract(invoice.getDiscountAmount())));
    }

    /** Tổng viện phí trước chiết khấu (đã gồm VAT): mức trần của chiết khấu (EX-14). */
    public static BigDecimal gross(Invoice invoice) {
        return money(invoice.getTotalServiceAmount().add(invoice.getTotalMedicineAmount())
                .add(invoice.getTotalVatAmount()));
    }
}
