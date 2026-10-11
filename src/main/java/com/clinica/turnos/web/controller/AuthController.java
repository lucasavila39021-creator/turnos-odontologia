package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.LoginRequest;
import com.clinica.turnos.application.dto.LoginResponse;
import com.clinica.turnos.application.dto.MeResponse;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth API — C-03 D-04.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        CustomUserDetails cud = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(LoginResponse.from(cud));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails cud)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(MeResponse.from(cud));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
