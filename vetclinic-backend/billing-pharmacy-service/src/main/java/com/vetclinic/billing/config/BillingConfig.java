package com.vetclinic.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BillingConfig {

    /** Mọi phép tính "bây giờ / năm hiện tại" theo giờ phòng khám đi qua Clock này (JVM chạy UTC, xem TimeZones). */
    @Bean
    public Clock billingClock(BillingProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
