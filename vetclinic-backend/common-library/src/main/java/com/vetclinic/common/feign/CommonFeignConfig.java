package com.vetclinic.common.feign;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Bean Feign dùng chung, chỉ nạp ở service có OpenFeign trên classpath.
 * Timeout bắt buộc khai báo trong application.yml của từng service (connect 3s / read 5s).
 */
@Configuration
@ConditionalOnClass(name = "feign.Feign")
public class CommonFeignConfig {

    @Bean
    public RequestInterceptor userHeaderPropagationInterceptor() {
        return new FeignClientInterceptor();
    }

    @Bean
    public ErrorDecoder customFeignErrorDecoder(ObjectMapper objectMapper) {
        return new CustomFeignErrorDecoder(objectMapper);
    }
}
