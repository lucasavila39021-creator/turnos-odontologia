package com.clinica.turnos.security;

import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * PermissionContext exposed as "authz" for @PreAuthorize — C-03 D-07.
 * Methods used as @PreAuthorize("@authz.isAdmin()") etc.
 */
@Component("authz")
public class AuthzService {

    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;

    public AuthzService(PacienteRepository pacienteRepository,
                        ProfesionalRepository profesionalRepository) {
        this.pacienteRepository = pacienteRepository;
        this.profesionalRepository = profesionalRepository;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        String target = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(target));
    }

    public boolean hasAnyRole(String... roles) {
        for (String r : roles) {
            if (hasRole(r)) {
                return true;
            }
        }
        return false;
    }

    public boolean isOwner(Long ownerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return false;
        }
        return cud.getId() != null && cud.getId().equals(ownerId);
    }

    public boolean isPacienteOwner(Long pacienteId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return false;
        }
        if (cud.getRol() != com.clinica.turnos.domain.model.Rol.PACIENTE || cud.getId() == null) {
            return false;
        }
        return isPropioPaciente(pacienteId);
    }

    /**
     * Ownership de ficha de paciente (C-08, RN-TU-09):
     * {@code paciente.usuario_id == current_user.id}. Ficha inexistente o
     * sin link → false (el servicio/controlador traduce a 403 para PACIENTE).
     */
    public boolean isPropioPaciente(Long pacienteId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return false;
        }
        if (cud.getId() == null || pacienteId == null) {
            return false;
        }
        return pacienteRepository.findById(pacienteId)
                .map(p -> cud.getId().equals(p.getUsuarioId()))
                .orElse(false);
    }

    /**
     * Ownership de ficha de profesional (C-09, RN-AU-04): espejo de
     * {@link #isPropioPaciente(Long)}.
     * {@code profesional.usuario_id == current_user.id}. Ficha inexistente o
     * sin link → false (el servicio traduce a 403 para ODONTOLOGO).
     */
    public boolean isPropioProfesional(Long profesionalId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
            return false;
        }
        if (cud.getId() == null || profesionalId == null) {
            return false;
        }
        return profesionalRepository.findById(profesionalId)
                .map(p -> cud.getId().equals(p.getUsuarioId()))
                .orElse(false);
    }

    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()));
    }
}
