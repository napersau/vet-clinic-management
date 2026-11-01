package com.vetclinic.billing.testsupport;

import com.vetclinic.billing.constant.PrescriptionStatus;
import com.vetclinic.billing.constant.VisitStatus;
import com.vetclinic.billing.dto.response.VisitSummaryResponse;
import com.vetclinic.billing.entity.Medicine;
import com.vetclinic.billing.entity.Prescription;
import com.vetclinic.billing.entity.PrescriptionItem;
import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.RoleCode;
import org.assertj.core.api.ThrowableAssert;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class TestData {

    /** 2026-10-30 09:00 giờ Việt Nam. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    public static final long OWNER = 5L;
    public static final long RECEPTIONIST = 9L;
    public static final long DOCTOR = 21L;
    public static final long OTHER_DOCTOR = 22L;

    private TestData() {
    }

    public static Medicine medicine(long id, String name, String price, int stock) {
        return Medicine.builder().id(id).code("MED_" + id).name(name).dosageForm("Viên nén").unit("Viên")
                .salePrice(new BigDecimal(price)).stockQuantity(stock).minStockAlert(10).active(true).version(0L).build();
    }

    public static VisitSummaryResponse visit(long id, VisitStatus status, Long doctorId) {
        return new VisitSummaryResponse(id, "VIS-2026-" + id, status, 11L, "Lu", OWNER, doctorId);
    }

    public static PrescriptionItem item(long id, Medicine medicine, int quantity) {
        return PrescriptionItem.builder().id(id).medicine(medicine).quantity(quantity).unitPrice(medicine.getSalePrice())
                .subtotal(medicine.getSalePrice().multiply(BigDecimal.valueOf(quantity))).dosage("1 viên")
                .frequency("2 lần/ngày").durationDays(5).build();
    }

    public static Prescription prescription(long id, long visitId, PrescriptionStatus status, PrescriptionItem... items) {
        Prescription prescription = Prescription.builder().id(id).visitId(visitId).status(status).build();
        for (PrescriptionItem item : items) {
            prescription.addItem(item);
        }
        return prescription;
    }

    public static void loginAs(long userId, RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    public static void assertError(ErrorCode expected, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(expected);
    }
}
