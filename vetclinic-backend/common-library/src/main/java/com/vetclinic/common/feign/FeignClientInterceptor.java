package com.vetclinic.common.feign;

import com.vetclinic.common.security.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Chuyển tiếp X-User-Id / X-User-Role của request hiện tại sang service được gọi,
 * để service đích áp dụng @PreAuthorize đúng người dùng.
 * Khi không có request (consumer RabbitMQ, scheduler) thì không gắn gì: hãy gọi endpoint /internal/** trong trường hợp đó.
 */
public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        copyHeader(request, template, SecurityConstants.HEADER_USER_ID);
        copyHeader(request, template, SecurityConstants.HEADER_USER_ROLE);
    }

    private static void copyHeader(HttpServletRequest request, RequestTemplate template, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) {
            template.header(name, value);
        }
    }
}
