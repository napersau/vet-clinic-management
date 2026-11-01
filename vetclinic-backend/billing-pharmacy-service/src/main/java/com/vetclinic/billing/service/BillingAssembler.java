package com.vetclinic.billing.service;

import com.vetclinic.billing.dto.response.InventoryTransactionResponse;
import com.vetclinic.billing.dto.response.PrescriptionItemResponse;
import com.vetclinic.billing.dto.response.PrescriptionResponse;
import com.vetclinic.billing.dto.response.PrescriptionSummaryResponse;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.InventoryTransaction;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entity -> DTO. Việc dựng từ entity (cần transaction để đọc collection lười) tách khỏi việc làm giàu bằng dữ liệu của
 * clinical-service (withVisit, gọi Feign ngoài transaction): withVisit nhận visit == null thì trả nguyên bản.
 * Phần hóa đơn (InvoiceResponse, InvoiceSummaryResponse, isPaid) và invoiceId / invoiceStatus của đơn thuốc thêm ở NV4b.
 */
@Component
public class BillingAssembler {

    // ------------------------------------------------------------------ đơn thuốc

    /** invoiceId / invoiceStatus để null cho tới khi có hóa đơn (NV4b). */
    public PrescriptionResponse toResponse(Prescription prescription) {
        List<PrescriptionItemResponse> items = prescription.getItems().stream().map(this::toResponse).toList();
        return new PrescriptionResponse(prescription.getId(), prescription.getVisitId(), null, null,
                prescription.getStatus(), prescription.getDoctorNotes(), total(prescription), null, null,
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

    // ------------------------------------------------------------------ kho

    public InventoryTransactionResponse toResponse(InventoryTransaction t) {
        return new InventoryTransactionResponse(t.getId(), t.getMedicineId(), t.getTransactionType(), t.getQuantity(),
                t.getBalanceAfter(), t.getReferenceId(), t.getNotes(), t.getCreatedBy(), t.getCreatedAt());
    }
}
