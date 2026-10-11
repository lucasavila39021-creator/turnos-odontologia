package com.clinica.turnos.web.mvc;

/**
 * Form plano del alta de turno SSR (C-11 task 3.1): todo String para
 * repoblar tras error sin 500. El controller parsea y mapea a
 * TurnoCreateRequest; ante 409/400 re-renderiza con los datos cargados.
 */
public class TurnoForm {

    private String pacienteId;
    private String pacienteBusqueda;
    private String profesionalId;
    private String especialidadId;
    private String fecha;
    private String horaInicio;

    public String getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(String pacienteId) {
        this.pacienteId = pacienteId;
    }

    public String getPacienteBusqueda() {
        return pacienteBusqueda;
    }

    public void setPacienteBusqueda(String pacienteBusqueda) {
        this.pacienteBusqueda = pacienteBusqueda;
    }

    public String getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(String profesionalId) {
        this.profesionalId = profesionalId;
    }

    public String getEspecialidadId() {
        return especialidadId;
    }

    public void setEspecialidadId(String especialidadId) {
        this.especialidadId = especialidadId;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(String horaInicio) {
        this.horaInicio = horaInicio;
    }
}
