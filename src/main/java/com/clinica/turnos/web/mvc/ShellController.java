package com.clinica.turnos.web.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Shell raíz (C-11 spec frontend-shell): GET / redirige a /login sin
 * sesión y al dashboard del rol con sesión (PACIENTE→mis turnos,
 * resto→agenda). Misma tabla que RoleRedirectAuthenticationSuccessHandler.
 */
@Controller
public class ShellController extends MvcBase {

    @GetMapping("/")
    public String raiz() {
        if (!isAuthenticated()) {
            return "redirect:/login";
        }
        if ("PACIENTE".equals(rolName())) {
            return "redirect:/mis-turnos";
        }
        return "redirect:/agenda";
    }
}
