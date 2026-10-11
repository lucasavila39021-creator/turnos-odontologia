package com.clinica.turnos.web.mvc;

import com.clinica.turnos.application.exception.TurnoForbiddenException;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Manejo UX de errores HTTP solo para los MVC SSR (C-11 spec "Manejo UX"):
 * 403→"No autorizado", 404→"No encontrado", 400→mensaje por campo.
 * Scope estricto por assignableTypes para NO tocar las APIs REST
 * (GlobalExceptionHandler sigue devolviendo JSON/RFC7807 en /api/*).
 * PACIENTE viendo turno/ficha ajena (servicio 404 para no revelar
 * existencia) se muestra como 403 "No autorizado" según tasks 2.2/4.2.
 */
@ControllerAdvice(assignableTypes = {
        ShellController.class,
        AgendaMvcController.class,
        TurnoMvcController.class,
        PacienteMvcController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MvcExceptionHandler extends MvcBase {

    @ExceptionHandler({TurnoForbiddenException.class, AccessDeniedException.class})
    public ModelAndView forbidden(RuntimeException ex) {
        ModelAndView mv = layout("error/403", "No autorizado", HttpStatus.FORBIDDEN);
        mv.addObject("mensaje", "No autorizado");
        mv.addObject("detalle", ex.getMessage() != null ? ex.getMessage() : "No tiene permiso");
        return mv;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView notFound(NoSuchElementException ex) {
        // Paciente fisgoneando ficha/turno ajeno: 403 sin revelar existencia.
        if ("PACIENTE".equals(rolName())) {
            ModelAndView mv = layout("error/403", "No autorizado", HttpStatus.FORBIDDEN);
            mv.addObject("mensaje", "No autorizado");
            mv.addObject("detalle", "No tiene permiso para ver este recurso");
            return mv;
        }
        ModelAndView mv = layout("error/404", "No encontrado", HttpStatus.NOT_FOUND);
        mv.addObject("mensaje", "No encontrado");
        mv.addObject("detalle", ex.getMessage() != null ? ex.getMessage() : "Recurso inexistente");
        return mv;
    }

    @ExceptionHandler(TurnoInvalidoException.class)
    public ModelAndView badRequest(TurnoInvalidoException ex) {
        ModelAndView mv = layout("error/400", "Solicitud inválida", HttpStatus.BAD_REQUEST);
        mv.addObject("mensaje", "Solicitud inválida");
        mv.addObject("fieldErrors", Map.of(ex.getField(), ex.getMessage()));
        mv.addObject("detalle", ex.getMessage());
        return mv;
    }
}
