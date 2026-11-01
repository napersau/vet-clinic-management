package com.vetclinic.clinical.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClinicConfig {

    /** Mọi phép tính "hôm nay / bây giờ" theo giờ phòng khám đi qua Clock này (JVM chạy UTC, xem TimeZones). */
    @Bean
    public Clock clinicClock(ClinicProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
