package com.vetclinic.appointment;

import com.vetclinic.common.util.TimeZones;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling cho tác vụ quét NO_SHOW (EX-01); @Scheduled phải khai báo zone = "Asia/Ho_Chi_Minh"
@EnableScheduling
@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "com.vetclinic.appointment.client")
@SpringBootApplication(scanBasePackages = "com.vetclinic")
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        // JVM phải ở UTC để khớp phiên JDBC (xem TimeZones); giờ phòng khám tính tường minh bằng Clock
        TimeZones.forceUtcDefault();
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
