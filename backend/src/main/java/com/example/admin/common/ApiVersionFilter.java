package com.example.admin.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final String V1_PREFIX = "/api/v1/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(V1_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String legacyUri = "/api/" + uri.substring(V1_PREFIX.length());
        filterChain.doFilter(new VersionedRequest(request, legacyUri), response);
    }

    private static class VersionedRequest extends HttpServletRequestWrapper {

        private final String legacyUri;

        VersionedRequest(HttpServletRequest request, String legacyUri) {
            super(request);
            this.legacyUri = legacyUri;
        }

        @Override
        public String getRequestURI() {
            return legacyUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            return new StringBuffer(getScheme() + "://" + getServerName()
                    + (getServerPort() == 80 || getServerPort() == 443 ? "" : ":" + getServerPort())
                    + legacyUri);
        }

        @Override
        public String getServletPath() {
            return legacyUri;
        }
    }
}
