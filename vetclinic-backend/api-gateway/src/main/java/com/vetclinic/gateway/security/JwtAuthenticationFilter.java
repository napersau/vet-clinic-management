package com.vetclinic.gateway.security;

import com.vetclinic.common.dto.ApiResponse;
import com.vetclinic.common.exception.ErrorCode;
import com.vetclinic.common.security.SecurityConstants;
import com.vetclinic.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * Cổng bảo vệ duy nhất của hệ thống:
 * 1. Luôn xóa X-User-Id / X-User-Role do client tự gửi (chống giả mạo quyền).
 * 2. Path public -> cho qua không gắn header.
 * 3. Path còn lại -> bắt buộc JWT hợp lệ, gắn header nội bộ từ claims; sai/hết hạn -> 401 ngay tại Gateway.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final JsonMapper jsonMapper;
    private final List<PathPattern> publicPatterns;

    public JwtAuthenticationFilter(JwtValidator jwtValidator, JsonMapper jsonMapper, GatewayAuthProperties properties) {
        this.jwtValidator = jwtValidator;
        this.jsonMapper = jsonMapper;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.publicPatterns = properties.publicPaths().stream().map(parser::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(SecurityConstants.HEADER_USER_ID);
                    headers.remove(SecurityConstants.HEADER_USER_ROLE);
                });
        ServerHttpRequest request = requestBuilder.build();

        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublic(request)) {
            return chain.filter(exchange.mutate().request(request).build());
        }

        Optional<Claims> claims = extractToken(request).flatMap(jwtValidator::parse);
        if (claims.isEmpty()) {
            return unauthorized(exchange.getResponse());
        }

        Object userId = claims.get().get(SecurityConstants.CLAIM_USER_ID);
        Object role = claims.get().get(SecurityConstants.CLAIM_ROLE);
        if (userId == null || role == null) {
            return unauthorized(exchange.getResponse());
        }

        ServerHttpRequest authenticated = request.mutate()
                .header(SecurityConstants.HEADER_USER_ID, String.valueOf(userId))
                .header(SecurityConstants.HEADER_USER_ROLE, String.valueOf(role))
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    @Override
    public int getOrder() {
        return -100; // Chạy sớm nhất, trước các filter định tuyến
    }

    private boolean isPublic(ServerHttpRequest request) {
        return publicPatterns.stream().anyMatch(p -> p.matches(request.getPath().pathWithinApplication()));
    }

    private static Optional<String> extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        }
        return Optional.empty();
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        response.setStatusCode(errorCode.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = jsonMapper.writeValueAsBytes(ApiResponse.error(errorCode));
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
