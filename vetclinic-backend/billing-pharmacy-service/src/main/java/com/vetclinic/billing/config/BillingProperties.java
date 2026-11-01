package com.vetclinic.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * @param vat  thuế suất GTGT (0.10 = 10%). Nhóm chưa chốt tỉ lệ thực tế (CLAUDE.md §8) nên mặc định 0
 * @param zone múi giờ vận hành của phòng khám, dùng cho năm trong mã hóa đơn
 */
@ConfigurationProperties(prefix = "vetclinic.billing")
public record BillingProperties(@DefaultValue Vat vat, @DefaultValue("Asia/Ho_Chi_Minh") String zone) {

    public BillingProperties {
        ZoneId.of(zone); // sai múi giờ thì dừng ngay lúc khởi động
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public record Vat(@DefaultValue("0.00") BigDecimal serviceRate, @DefaultValue("0.00") BigDecimal medicineRate) {

        public Vat {
            // Cột invoice_items.vat_rate là DECIMAL(4,2) và tỉ lệ là phân số, nên 0 <= rate < 1
            requireFraction("service-rate", serviceRate);
            requireFraction("medicine-rate", medicineRate);
        }

        private static void requireFraction(String name, BigDecimal rate) {
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException("vetclinic.billing.vat." + name + " phải nằm trong [0, 1), ví dụ 0.10");
            }
        }
    }
}
