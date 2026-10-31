package com.vetclinic.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cho phép từng service khai báo thêm đường dẫn public, ví dụ auth-service:
 * <pre>
 * vetclinic:
 *   security:
 *     public-paths:
 *       - /api/v1/auth/login
 * </pre>
 */
@ConfigurationProperties(prefix = "vetclinic.security")
public record VetclinicSecurityProperties(List<String> publicPaths) {

    public VetclinicSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
