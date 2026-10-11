package com.clinica.turnos.security;

import com.clinica.turnos.application.dto.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Success handler — C-03 D-05.
 * JSON: 200 LoginResponse; HTML: 302 redirect by rol.
 */
@Component
public class RoleRedirectAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    public RoleRedirectAuthenticationSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (isJson(request)) {
            CustomUserDetails cud = (CustomUserDetails) authentication.getPrincipal();
            LoginResponse lr = LoginResponse.from(cud);
            response.setStatus(200);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), lr);
            return;
        }
        String target = resolveTarget(authentication);
        response.sendRedirect(target);
    }

    private boolean isJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private String resolveTarget(Authentication authentication) {
        boolean isPaciente = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PACIENTE"));
        if (isPaciente) {
            return "/mis-turnos";
        }
        // ODONTOLOGO, SECRETARIA, ADMIN -> /agenda per spec
        return "/agenda";
    }
}
