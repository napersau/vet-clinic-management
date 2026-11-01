package com.vetclinic.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Toàn bộ phép tính tiền nằm ở đây (BigDecimal, làm tròn HALF_UP 2 chữ số, CLAUDE.md §5.8).
 * Phần tính hóa đơn (buildItem: subtotal = quantity * unitPrice, vat = subtotal * rate; recalculate / gross:
 * final = Σsubtotal + Σvat - discount) thêm ở NV4b cùng các entity hóa đơn.
 */
public final class InvoiceCalculator {

    private InvoiceCalculator() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
