package com.clinica.turnos.web.mvc;

import com.clinica.turnos.security.CustomUserDetails;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;

/**
 * Base de los MvcControllers SSR (C-11 D-01): thin, model attributes,
 * redirects por rol. Sin thymeleaf-extras-springsecurity (no disponible
 * offline): el rol/username viaja en el model y los fragmentos ocultan
 * con th:if; el backend (@PreAuthorize + scoping de servicio) manda.
 */
public abstract class MvcBase {

    /** Zona fija RN-GL-02: nunca systemDefault. */
    protected static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    protected Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    protected boolean isAuthenticated() {
        Authentication a = auth();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(String.valueOf(a.getPrincipal()));
    }

    /** Rol actual (PACIENTE/ODONTOLOGO/SECRETARIA/ADMIN) o "ANONIMO". */
    protected String rolName() {
        Authentication a = auth();
        if (a == null) {
            return "ANONIMO";
        }
        if (a.getPrincipal() instanceof CustomUserDetails cud && cud.getRol() != null) {
            return cud.getRol().name();
        }
        return a.getAuthorities().stream()
                .map(g -> g.getAuthority())
                .filter(g -> g.startsWith("ROLE_"))
                .map(g -> g.substring("ROLE_".length()))
                .findFirst()
                .orElse("ANONIMO");
    }

    protected String username() {
        Authentication a = auth();
        if (a == null) {
            return "";
        }
        if (a.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getUsername();
        }
        return String.valueOf(a.getPrincipal());
    }

    protected Long userId() {
        Authentication a = auth();
        if (a != null && a.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }

    protected boolean hasAny(String... roles) {
        String actual = rolName();
        for (String r : roles) {
            if (r.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vista layout con el fragmento de contenido indicado.
     * Las páginas definen {@code th:fragment="contenido"} y el layout las
     * incluye con {@code ${vista} :: contenido}.
     */
    protected ModelAndView layout(String vista, String titulo, HttpStatus status) {
        ModelAndView mv = new ModelAndView("layout");
        mv.addObject("vista", vista);
        mv.addObject("titulo", titulo);
        mv.addObject("rol", rolName());
        mv.addObject("username", username());
        if (status != null) {
            mv.setStatus(status);
        }
        return mv;
    }

    protected ModelAndView layout(String vista, String titulo) {
        return layout(vista, titulo, null);
    }
}
