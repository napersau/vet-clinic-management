package com.vetclinic.clinical.testsupport;

import com.vetclinic.clinical.constant.PetGender;
import com.vetclinic.clinical.constant.ServiceCategory;
import com.vetclinic.clinical.constant.VisitStatus;
import com.vetclinic.clinical.entity.ClinicService;
import com.vetclinic.clinical.entity.Pet;
import com.vetclinic.clinical.entity.PetVisit;
import com.vetclinic.clinical.entity.Species;
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
    public static final long OTHER_OWNER = 6L;
    public static final long RECEPTIONIST = 9L;
    public static final long DOCTOR = 21L;
    public static final long OTHER_DOCTOR = 22L;

    private TestData() {
    }

    public static Species dog() {
        return Species.builder().id(1L).code("DOG").name("Chó").build();
    }

    public static Species cat() {
        return Species.builder().id(2L).code("CAT").name("Mèo").build();
    }

    public static Pet pet(long id, Species species) {
        return Pet.builder().id(id).ownerId(OWNER).species(species).petCode("PET-2026-" + id).name("Lu")
                .gender(PetGender.MALE).build();
    }

    public static PetVisit visit(long id, VisitStatus status, Long doctorId) {
        return PetVisit.builder().id(id).visitCode("VIS-2026-" + id).pet(pet(11L, dog())).doctorId(doctorId)
                .assignedRoom(doctorId == null ? null : "P01").receptionistId(RECEPTIONIST).status(status)
                .checkedInAt(Instant.parse("2026-10-30T01:00:00Z")).build();
    }

    public static ClinicService service(long id, String code, ServiceCategory category, String price) {
        return ClinicService.builder().id(id).code(code).name("Dịch vụ " + code).category(category)
                .price(new BigDecimal(price)).active(true).build();
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
