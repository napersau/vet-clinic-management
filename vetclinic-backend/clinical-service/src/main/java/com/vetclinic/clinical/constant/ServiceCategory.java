package com.vetclinic.clinical.constant;

import java.util.EnumSet;
import java.util.Set;

/** Khớp ENUM services.category. */
public enum ServiceCategory {
    EXAMINATION,
    VACCINATION,
    DEWORMING,
    EXTERNAL_PARASITE,
    INJECTION_INFUSION,
    LABORATORY,
    IMAGING,
    SURGERY,
    PROCEDURE;

    /** Dịch vụ cận lâm sàng: đi qua hàng đợi Lab / CĐHA. */
    public static final Set<ServiceCategory> PARACLINICAL = EnumSet.of(LABORATORY, IMAGING);

    public boolean isParaclinical() {
        return PARACLINICAL.contains(this);
    }
}
