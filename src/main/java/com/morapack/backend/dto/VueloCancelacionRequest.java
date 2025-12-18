package com.morapack.backend.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO para recibir cancelaciones de vuelos
 *
 * Formato esperado por línea: SPIM-SKBO-04:35-08:51-0340
 */
public class VueloCancelacionRequest {

    private LocalDate fecha; // Fecha del vuelo a cancelar
    private List<String> vuelosCancelados; // Lista de strings con formato especificado

    public VueloCancelacionRequest() {
        this.vuelosCancelados = new ArrayList<>();
    }

    public VueloCancelacionRequest(LocalDate fecha, List<String> vuelosCancelados) {
        this.fecha = fecha;
        this.vuelosCancelados = vuelosCancelados != null ? vuelosCancelados : new ArrayList<>();
    }

    // ============================================
    // GETTERS Y SETTERS
    // ============================================

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public List<String> getVuelosCancelados() {
        return vuelosCancelados;
    }

    public void setVuelosCancelados(List<String> vuelosCancelados) {
        this.vuelosCancelados = vuelosCancelados;
    }

    // ============================================
    // MÉTODOS DE VALIDACIÓN
    // ============================================

    /**
     * Valida que la request sea correcta
     */
    public boolean esValida() {
        if (fecha == null) {
            return false;
        }
        if (vuelosCancelados == null || vuelosCancelados.isEmpty()) {
            return false;
        }
        // Validar que cada línea tenga el formato correcto
        for (String linea : vuelosCancelados) {
            if (!esLineaValida(linea)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida que una línea tenga el formato: ORIGEN-DESTINO-HH:mm-HH:mm-NNNN
     */
    private boolean esLineaValida(String linea) {
        if (linea == null || linea.trim().isEmpty()) {
            return false;
        }

        String[] partes = linea.trim().split("-");
        if (partes.length != 5) {
            return false;
        }

        // Validar formato de horas (HH:mm)
        if (!partes[2].matches("\\d{2}:\\d{2}") || !partes[3].matches("\\d{2}:\\d{2}")) {
            return false;
        }

        // Validar capacidad (número de 4 dígitos)
        if (!partes[4].matches("\\d{4}")) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "VueloCancelacionRequest{" +
                "fecha=" + fecha +
                ", cantidadVuelos=" + (vuelosCancelados != null ? vuelosCancelados.size() : 0) +
                '}';
    }
}