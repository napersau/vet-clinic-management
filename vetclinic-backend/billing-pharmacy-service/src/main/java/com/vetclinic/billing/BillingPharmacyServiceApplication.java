package com.vetclinic.billing;

import com.vetclinic.common.util.TimeZones;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@ConfigurationPropertiesScan
@EnableFeignClients(basePackages = "com.vetclinic.billing.client")
@SpringBootApplication(scanBasePackages = "com.vetclinic")
public class BillingPharmacyServiceApplication {

    public static void main(String[] args) {
        // JVM phải ở UTC để khớp phiên JDBC (xem TimeZones); giờ phòng khám tính tường minh bằng Clock
        TimeZones.forceUtcDefault();
        SpringApplication.run(BillingPharmacyServiceApplication.class, args);
    }
}
