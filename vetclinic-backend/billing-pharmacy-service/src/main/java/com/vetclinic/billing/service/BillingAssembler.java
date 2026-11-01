package com.vetclinic.billing.service;

import com.vetclinic.billing.constant.PaymentStatus;
import com.vetclinic.billing.dto.response.InventoryTransactionResponse;
import com.vetclinic.billing.dto.response.InvoiceItemResponse;
import com.vetclinic.billing.dto.response.InvoiceResponse;
import com.vetclinic.billing.dto.response.InvoiceSummaryResponse;
import com.vetclinic.billing.dto.response.PrescriptionItemResponse;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Invoice;
import com.vetclinic.billing.entity.InvoiceItem;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entity -> DTO. Việc dựng từ entity (cần transaction để đọc collection lười) tách khỏi việc làm giàu bằng dữ liệu của
 * clinical-service (withVisit, gọi Feign ngoài transaction): withVisit nhận visit == null thì trả nguyên bản.
 */
@Component
public class BillingAssembler {

    // ------------------------------------------------------------------ đơn thuốc

    public PrescriptionResponse toResponse(Prescription prescription, Invoice invoice) {
        List<PrescriptionItemResponse> items = prescription.getItems().stream().map(this::toResponse).toList();
        return new PrescriptionResponse(prescription.getId(), prescription.getVisitId(), null, null,
                prescription.getStatus(), prescription.getDoctorNotes(), total(prescription),
                invoice == null ? null : invoice.getId(), invoice == null ? null : invoice.getPaymentStatus(),
                prescription.getDispensedBy(), prescription.getDispensedAt(), prescription.getCreatedAt(), items);
    }

    public PrescriptionResponse withVisit(PrescriptionResponse r, VisitSummaryResponse visit) {
        if (visit == null) {
            return r;
        }
        return new PrescriptionResponse(r.id(), r.visitId(), visit.visitCode(), visit.petName(), r.status(),
                r.doctorNotes(), r.totalAmount(), r.invoiceId(), r.invoiceStatus(), r.dispensedBy(), r.dispensedAt(),
                r.createdAt(), r.items());
    }

    public PrescriptionSummaryResponse toSummary(Prescription prescription) {
        return new PrescriptionSummaryResponse(prescription.getId(), prescription.getVisitId(), null, null,
                prescription.getStatus(), prescription.getItems().size(), total(prescription),
                prescription.getCreatedAt(), prescription.getDispensedAt());
    }

    public PrescriptionSummaryResponse withVisit(PrescriptionSummaryResponse r, VisitSummaryResponse visit) {
        if (visit == null) {
            return r;
        }
        return new PrescriptionSummaryResponse(r.id(), r.visitId(), visit.visitCode(), visit.petName(), r.status(),
                r.itemCount(), r.totalAmount(), r.createdAt(), r.dispensedAt());
    }

    private PrescriptionItemResponse toResponse(PrescriptionItem item) {
        Medicine medicine = item.getMedicine();
        return new PrescriptionItemResponse(item.getId(), medicine.getId(), medicine.getCode(), medicine.getName(),
                medicine.getActiveIngredient(), medicine.getUnit(), item.getQuantity(), item.getUnitPrice(),
                item.getSubtotal(), item.getDosage(), item.getFrequency(), item.getDurationDays(),
                item.getInstructions());
    }

    private static BigDecimal total(Prescription prescription) {
        return InvoiceCalculator.money(prescription.getItems().stream().map(PrescriptionItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ------------------------------------------------------------------ hóa đơn

    public InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceItemResponse> items = invoice.getItems().stream().map(this::toResponse).toList();
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceCode(), invoice.getVisitId(), null, null, null,
                invoice.getPrescriptionId(), invoice.getCashierId(), invoice.getTotalServiceAmount(),
                invoice.getTotalMedicineAmount(), invoice.getTotalVatAmount(), invoice.getDiscountAmount(),
                invoice.getFinalAmount(), invoice.getPaymentStatus(), invoice.getPaidAt(), invoice.getNotes(),
                invoice.getCreatedAt(), items);
    }

    public InvoiceResponse withVisit(InvoiceResponse r, VisitSummaryResponse visit) {
        if (visit == null) {
            return r;
        }
        return new InvoiceResponse(r.id(), r.invoiceCode(), r.visitId(), visit.visitCode(), visit.petName(),
                visit.ownerId(), r.prescriptionId(), r.cashierId(), r.totalServiceAmount(), r.totalMedicineAmount(),
                r.totalVatAmount(), r.discountAmount(), r.finalAmount(), r.paymentStatus(), r.paidAt(), r.notes(),
                r.createdAt(), r.items());
    }

    public InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(invoice.getId(), invoice.getInvoiceCode(), invoice.getVisitId(), null, null,
                invoice.getPrescriptionId(), invoice.getFinalAmount(), invoice.getPaymentStatus(), invoice.getPaidAt(),
                invoice.getCreatedAt());
    }

    public InvoiceSummaryResponse withVisit(InvoiceSummaryResponse r, VisitSummaryResponse visit) {
        if (visit == null) {
            return r;
        }
        return new InvoiceSummaryResponse(r.id(), r.invoiceCode(), r.visitId(), visit.visitCode(), visit.petName(),
                r.prescriptionId(), r.finalAmount(), r.paymentStatus(), r.paidAt(), r.createdAt());
    }

    private InvoiceItemResponse toResponse(InvoiceItem item) {
        return new InvoiceItemResponse(item.getId(), item.getItemType(), item.getReferenceId(), item.getItemName(),
                item.getQuantity(), item.getUnitPrice(), item.getVatRate(), item.getVatAmount(), item.getSubtotal());
    }

    // ------------------------------------------------------------------ kho

    public InventoryTransactionResponse toResponse(InventoryTransaction t) {
        return new InventoryTransactionResponse(t.getId(), t.getMedicineId(), t.getTransactionType(), t.getQuantity(),
                t.getBalanceAfter(), t.getReferenceId(), t.getNotes(), t.getCreatedBy(), t.getCreatedAt());
    }

    /** Hóa đơn đã thanh toán thì đơn thuốc mới được phát. */
    public static boolean isPaid(Invoice invoice) {
        return invoice != null && invoice.getPaymentStatus() == PaymentStatus.PAID;
    }
}
