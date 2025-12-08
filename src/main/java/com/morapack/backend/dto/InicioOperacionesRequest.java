package com.morapack.backend.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * DTO para recibir la fecha/hora de inicio de operaciones.
 * Usa String para evitar problemas de serialización con Jackson.
 */
public class InicioOperacionesRequest {

    private String fechaHoraInicio;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public InicioOperacionesRequest() {
    }

    public InicioOperacionesRequest(String fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public String getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(String fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    /**
     * Convierte el string a LocalDateTime.
     *
     * @return LocalDateTime parseado, o null si el string es null/vacío
     * @throws DateTimeParseException si el formato es inválido
     */
    public LocalDateTime toLocalDateTime() {
        if (fechaHoraInicio == null || fechaHoraInicio.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(fechaHoraInicio, FORMATTER);
    }

    /**
     * Valida si el formato del string es correcto sin lanzar excepción.
     *
     * @return true si el formato es válido, false en caso contrario
     */
    public boolean isValid() {
        try {
            return toLocalDateTime() != null;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "InicioOperacionesRequest{" +
                "fechaHoraInicio='" + fechaHoraInicio + '\'' +
                '}';
    }
}