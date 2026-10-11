package com.clinica.turnos.web;

import com.clinica.turnos.application.exception.DuplicateDniException;
import com.clinica.turnos.application.exception.DuplicateEspecialidadException;
import com.clinica.turnos.application.exception.DuplicateLinkException;
import com.clinica.turnos.application.exception.DuplicateMatriculaException;
import com.clinica.turnos.application.exception.DuplicateUsuarioException;
import com.clinica.turnos.application.exception.EspecialidadConProfesionalesException;
import com.clinica.turnos.application.exception.InvalidPacienteException;
import com.clinica.turnos.application.exception.InvalidProfesionalException;
import com.clinica.turnos.application.exception.LastAdminException;
import com.clinica.turnos.application.exception.ProfesionalConTurnosException;
import com.clinica.turnos.application.exception.TurnoForbiddenException;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.exception.TurnoOcupadoException;
import com.clinica.turnos.security.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * RFC7807 handlers — C-03 D-08, envelope unificado C-12 D-04.
 * Todos los errores API responden envelope
 * {type,title,status,detail,error,message,fieldErrors?,sugeridos?}:
 * `detail` se mantiene por compat con tests C-03..C-10; `error`/`message`
 * son el envelope unificado (401 genérico, sin enumeración).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> envelope(HttpStatus status, String title,
                                        String detail, String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("error", error != null ? error : title);
        body.put("message", detail);
        return body;
    }

    private Map<String, Object> envelopeWithFields(HttpStatus status, String title,
                                                  String detail, String error,
                                                  Map<String, String> fieldErrors) {
        Map<String, Object> body = envelope(status, title, detail, error);
        body.put("fieldErrors", fieldErrors);
        return body;
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredentials(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(envelope(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        "Credenciales inválidas", "Credenciales inválidas"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(envelope(HttpStatus.FORBIDDEN, "Forbidden", "Forbidden", "Forbidden"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        Map<String, Object> body = envelope(HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests", "Demasiados intentos. Reintentá en 60s.",
                "Demasiados intentos");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", "Validation failed", "Validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * C-12 task 2.3: violaciones de Bean Validation en @RequestParam/@PathVariable
     * validados con @Validated → 400 con fieldErrors, nunca 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> {
                            String path = v.getPropertyPath() != null
                                    ? v.getPropertyPath().toString() : "param";
                            int dot = path.lastIndexOf('.');
                            return dot >= 0 ? path.substring(dot + 1) : path;
                        },
                        v -> v.getMessage() != null ? v.getMessage() : "inválido",
                        (a, b) -> a));
        if (fieldErrors.isEmpty()) {
            fieldErrors = Map.of("request", "solicitud inválida");
        }
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", "Validation failed", "Validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * C-12 task 2.3: JSON malformado o tipo incompatible en el body
     * → 400 con fieldErrors, nunca 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(
            HttpMessageNotReadableException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", "cuerpo JSON inválido o malformado",
                "cuerpo JSON inválido", Map.of("body", "JSON malformado o tipo inválido"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<Map<String, Object>> handleLastAdmin(LastAdminException ex) {
        Map<String, Object> body = envelope(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicateUsuarioException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateUsuarioException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicateDniException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateDni(DuplicateDniException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage(),
                Map.of("dni", ex.getMessage()));
        body.put("pacienteId", ex.getPacienteId());
        body.put("pacienteUri", "/api/pacientes/" + ex.getPacienteId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicateEspecialidadException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEspecialidad(DuplicateEspecialidadException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(EspecialidadConProfesionalesException.class)
    public ResponseEntity<Map<String, Object>> handleEspecialidadCautiva(EspecialidadConProfesionalesException ex) {
        Map<String, Object> body = envelope(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicateLinkException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateLink(DuplicateLinkException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidPacienteException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPaciente(InvalidPacienteException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicateMatriculaException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateMatricula(DuplicateMatriculaException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidProfesionalException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidProfesional(InvalidProfesionalException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ProfesionalConTurnosException.class)
    public ResponseEntity<Map<String, Object>> handleProfesionalConTurnos(ProfesionalConTurnosException ex) {
        Map<String, Object> body = envelope(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(TurnoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleTurnoInvalido(TurnoInvalidoException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", ex.getMessage(), ex.getMessage(),
                Map.of(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(TurnoOcupadoException.class)
    public ResponseEntity<Map<String, Object>> handleTurnoOcupado(TurnoOcupadoException ex) {
        Map<String, Object> body = envelope(HttpStatus.CONFLICT,
                "Conflict", ex.getMessage(), ex.getMessage());
        body.put("sugeridos", ex.getSugeridos());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(TurnoForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleTurnoForbidden(TurnoForbiddenException ex) {
        Map<String, Object> body = envelope(HttpStatus.FORBIDDEN,
                "Forbidden", ex.getMessage(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex) {
        String field = "username";
        String constraint = rootMessage(ex);
        if (constraint.contains("uk_usuario_email")) {
            field = "email";
        } else if (constraint.contains("uk_usuario_username")) {
            field = "username";
        } else if (constraint.contains("uk_paciente_dni")) {
            field = "dni";
        } else if (constraint.contains("uk_paciente_usuario")) {
            field = "usuarioId";
        } else if (constraint.contains("uk_especialidad_nombre")) {
            field = "nombre";
        } else if (constraint.contains("uk_profesional_matricula")) {
            field = "matricula";
        } else if (constraint.contains("uk_profesional_usuario")) {
            field = "usuarioId";
        }
        Map<String, Object> body = envelopeWithFields(HttpStatus.CONFLICT,
                "Conflict", "Recurso duplicado", "Recurso duplicado",
                Map.of(field, "ya registrado"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "solicitud inválida";
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", msg, msg, Map.of("nombre", msg));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "No encontrado";
        Map<String, Object> body = envelope(HttpStatus.NOT_FOUND,
                "Not Found", msg, msg);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Fecha/query param con formato inválido (C-09): 400 con fieldErrors.fecha.
     * Red de seguridad — los controllers de turno parsean manual y lanzan
     * TurnoInvalidoException; esto cubre conversiones automáticas restantes.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Map<String, Object>> handleFechaInvalida(DateTimeParseException ex) {
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", "formato de fecha inválido (AAAA-MM-DD)",
                "formato de fecha inválido",
                Map.of("fecha", "formato de fecha inválido (AAAA-MM-DD)"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String campo = ex.getName() != null ? ex.getName() : "fecha";
        String clave = campo.equals("fecha") || campo.equals("desde") || campo.equals("hasta")
                ? "fecha" : campo;
        Map<String, Object> body = envelopeWithFields(HttpStatus.BAD_REQUEST,
                "Bad Request", "parámetro inválido: " + campo, "parámetro inválido",
                Map.of(clave, "valor inválido"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private String rootMessage(DataIntegrityViolationException ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(' ');
            }
            t = t.getCause();
        }
        return sb.toString();
    }
}
