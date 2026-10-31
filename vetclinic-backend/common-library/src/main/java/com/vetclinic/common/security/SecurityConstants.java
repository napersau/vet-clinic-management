package com.vetclinic.common.security;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    // Header nội bộ do API Gateway gắn sau khi xác thực JWT
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    // Tên claim trong JWT (auth-service ký, api-gateway đọc)
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROOM_NUMBER = "roomNumber";

    public static final String ROLE_PREFIX = "ROLE_";

    // Endpoint nội bộ service-to-service: không được Gateway định tuyến ra ngoài
    public static final String INTERNAL_PATH_PATTERN = "/internal/**";
}
