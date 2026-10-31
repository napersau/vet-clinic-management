package com.vetclinic.auth;

import com.vetclinic.common.util.TimeZones;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// scanBasePackages = "com.vetclinic" để nạp GlobalExceptionHandler, CommonSecurityConfig... từ common-library
// @EnableScheduling cho tác vụ dọn Refresh Token hết hạn
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "com.vetclinic")
public class AuthServiceApplication {

    public static void main(String[] args) {
        // JVM phải ở UTC để khớp phiên JDBC (xem TimeZones); giờ phòng khám tính tường minh bằng Clock
        TimeZones.forceUtcDefault();
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
