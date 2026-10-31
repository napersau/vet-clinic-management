package com.vetclinic.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Đọc X-User-Id / X-User-Role do Gateway gắn và nạp vào SecurityContext để @PreAuthorize hoạt động.
 * Cố ý KHÔNG đánh dấu @Component: nếu là bean, Spring Boot sẽ tự đăng ký thêm vào servlet chain và filter chạy 2 lần.
 * Filter chỉ được thêm vào SecurityFilterChain trong CommonSecurityConfig.
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(SecurityConstants.HEADER_USER_ID);
        String userRole = request.getHeader(SecurityConstants.HEADER_USER_ROLE);

        if (userId != null && !userId.isBlank() && userRole != null && !userRole.isBlank()) {
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + userRole.trim());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId.trim(), null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
