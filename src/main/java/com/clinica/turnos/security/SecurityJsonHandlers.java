package com.clinica.turnos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Handlers for JSON vs redirect — C-03 D-05, D-08.
 */
@Component
public class SecurityJsonHandlers {

    private final ObjectMapper objectMapper;

    public SecurityJsonHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationFailureHandler failureHandler() {
        return (request, response, exception) -> {
            if (isJson(request)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/problem+json");
                Map<String, Object> body = Map.of(
                        "type", "about:blank",
                        "title", "Unauthorized",
                        "status", 401,
                        "detail", "Credenciales inválidas",
                        "error", "Credenciales inválidas",
                        "message", "Credenciales inválidas"
                );
                objectMapper.writeValue(response.getWriter(), body);
            } else {
                response.sendRedirect("/login?error");
            }
        };
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, authException) -> {
            if (isJson(request)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/problem+json");
                Map<String, Object> body = Map.of(
                        "type", "about:blank",
                        "title", "Unauthorized",
                        "status", 401,
                        "detail", "Credenciales inválidas",
                        "error", "Credenciales inválidas",
                        "message", "Credenciales inválidas"
                );
                objectMapper.writeValue(response.getWriter(), body);
            } else {
                response.sendRedirect("/login");
            }
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (isJson(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/problem+json");
                Map<String, Object> body = Map.of(
                        "type", "about:blank",
                        "title", "Forbidden",
                        "status", 403,
                        "detail", "Forbidden",
                        "error", "Forbidden",
                        "message", "Forbidden"
                );
                objectMapper.writeValue(response.getWriter(), body);
            } else {
                // For HTML, Spring default would be 403; we redirect to login? Keep 403 for API test clarity
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/problem+json");
                Map<String, Object> body = Map.of(
                        "type", "about:blank",
                        "title", "Forbidden",
                        "status", 403,
                        "detail", "Forbidden",
                        "error", "Forbidden",
                        "message", "Forbidden"
                );
                objectMapper.writeValue(response.getWriter(), body);
            }
        };
    }

    private boolean isJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        if (accept != null && accept.contains("application/problem+json")) {
            return true;
        }
        // For API paths, default to JSON
        String path = request.getServletPath();
        if (path != null && path.startsWith("/api/")) {
            // API always JSON unless explicitly text/html
            if (accept != null && accept.contains("text/html")) {
                return false;
            }
            return true;
        }
        return false;
    }
}
