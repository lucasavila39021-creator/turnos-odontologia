package com.clinica.turnos.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limit filter for POST /api/auth/login and POST /login — C-03 D-06.
 * Key = IP + username (case-insensitive). 429 with Retry-After if exceeded.
 * Only counts failures (401 or 302 error); success does not consume.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        // handle contextPath prefix
        String context = request.getContextPath();
        if (context != null && !context.isBlank() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        String method = request.getMethod();
        boolean isLogin = "POST".equalsIgnoreCase(method)
                && ("/api/auth/login".equals(path) || "/login".equals(path));
        return !isLogin;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServletRequest effectiveRequest = request;
        String cachedBodyStr = null;
        if (isJson(request)) {
            CachedBodyRequestWrapper cached = new CachedBodyRequestWrapper(request);
            effectiveRequest = cached;
            cachedBodyStr = new String(cached.getCachedBody(), StandardCharsets.UTF_8);
        }

        String username = extractUsername(effectiveRequest, cachedBodyStr);
        String clientIp = extractClientIp(request);
        String key = rateLimitService.keyFor(clientIp, username);

        // Try consume before chain; if limited, return 429 immediately
        boolean allowed = rateLimitService.tryConsume(key);
        if (!allowed) {
            write429(response);
            return;
        }

        StatusExposingResponseWrapper statusWrapper = new StatusExposingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, statusWrapper);

        int status = statusWrapper.getStatus();
        boolean isFailure = false;
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            isFailure = true;
        } else if (status == 302) {
            String location = statusWrapper.getHeader("Location");
            if (location != null && location.contains("error")) {
                isFailure = true;
            }
        }
        // Success should not count: refund token
        if (!isFailure) {
            rateLimitService.refund(key);
        }
    }

    private boolean isJson(HttpServletRequest request) {
        String ct = request.getContentType();
        return ct != null && ct.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String extractUsername(HttpServletRequest request, String cachedBody) {
        String param = request.getParameter("username");
        if (param != null && !param.isBlank()) {
            return param;
        }
        if (isJson(request) && cachedBody != null && !cachedBody.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(cachedBody);
                JsonNode u = node.get("username");
                if (u != null && u.isTextual()) {
                    return u.asText();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return "";
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void write429(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(rateLimitService.getWindowSeconds()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType("application/problem+json");
        String body = """
                {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Demasiados intentos. Reintentá en 60s."}
                """;
        response.getWriter().write(body);
    }

    static class StatusExposingResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private int httpStatus = 200;

        public StatusExposingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.httpStatus = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.httpStatus = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.httpStatus = sc;
        }

        @Override
        public int getStatus() {
            return httpStatus;
        }
    }
}
