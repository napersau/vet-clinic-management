package com.vetclinic.clinical.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param zone múi giờ vận hành của phòng khám, dùng để xác định "hôm nay" khi check-in lịch hẹn
 */
@ConfigurationProperties(prefix = "vetclinic.clinic")
public record ClinicProperties(@DefaultValue("Asia/Ho_Chi_Minh") String zone) {

    public ClinicProperties {
        ZoneId.of(zone); // sai múi giờ thì dừng ngay lúc khởi động
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
