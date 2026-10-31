package com.vetclinic.appointment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BookingConfig {

    /** Mọi phép tính "hôm nay / bây giờ" đi qua Clock này (giờ phòng khám, và thay được trong test). */
    @Bean
    public Clock bookingClock(BookingProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
