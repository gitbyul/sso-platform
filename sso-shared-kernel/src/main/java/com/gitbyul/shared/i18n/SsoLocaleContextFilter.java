package com.gitbyul.shared.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MVC·예외 처리기에서 동일 {@link java.util.Locale}을 쓰도록 스레드에 바인딩한다. 테넌트 식별 필터 이후에 동작해야 한다.
 */
public class SsoLocaleContextFilter extends OncePerRequestFilter implements Ordered {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

    private final SsoRequestLocaleResolver requestLocaleResolver;

    public SsoLocaleContextFilter(SsoRequestLocaleResolver requestLocaleResolver) {
        this.requestLocaleResolver = requestLocaleResolver;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            LocaleContextHolder.setLocale(requestLocaleResolver.resolveLocale(request), true);
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
