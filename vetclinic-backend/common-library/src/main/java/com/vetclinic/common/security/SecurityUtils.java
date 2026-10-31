package com.vetclinic.common.security;

import com.vetclinic.common.exception.AppException;
import com.vetclinic.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.stream.Stream;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    public static Optional<RoleCode> getCurrentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(SecurityConstants.ROLE_PREFIX))
                .map(a -> a.substring(SecurityConstants.ROLE_PREFIX.length()))
                .flatMap(SecurityUtils::toRoleCode)
                .findFirst();
    }

    private static Stream<RoleCode> toRoleCode(String code) {
        try {
            return Stream.of(RoleCode.valueOf(code));
        } catch (IllegalArgumentException e) {
            return Stream.empty();
        }
    }
}
