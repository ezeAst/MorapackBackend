package com.morapack.backend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Servicio centralizado para manejar el tiempo simulado en el sistema.
 * Permite que todas las operaciones trabajen con un tiempo controlado
 * en lugar del tiempo real del sistema.
 */
@Service
public class TiempoSimuladoService {

    private LocalDateTime tiempoSimulado;
    private boolean usandoTiempoSimulado = false;

    /**
     * Inicializa el tiempo simulado con una fecha/hora específica
     */
    public void iniciarSimulacion(LocalDateTime fechaHoraInicio) {
        this.tiempoSimulado = fechaHoraInicio;
        this.usandoTiempoSimulado = true;
        System.out.println("⏰ Tiempo simulado iniciado en: " + fechaHoraInicio);
    }

    /**
     * Detiene la simulación y vuelve a usar tiempo real
     */
    public void detenerSimulacion() {
        this.usandoTiempoSimulado = false;
        this.tiempoSimulado = null;
        System.out.println("⏰ Simulación detenida, volviendo a tiempo real");
    }

    /**
     * Avanza el tiempo simulado en la cantidad especificada de segundos
     */
    public void avanzarTiempo(long segundos) {
        if (usandoTiempoSimulado && tiempoSimulado != null) {
            tiempoSimulado = tiempoSimulado.plusSeconds(segundos);
            System.out.println("⏰ Tiempo avanzado a: " + tiempoSimulado);
        }
    }

    /**
     * Obtiene el tiempo actual (simulado o real según el estado)
     */
    public LocalDateTime obtenerTiempoActual() {
        if (usandoTiempoSimulado && tiempoSimulado != null) {
            return tiempoSimulado;
        }
        return LocalDateTime.now();
    }

    /**
     * Verifica si se está usando tiempo simulado
     */
    public boolean isUsandoTiempoSimulado() {
        return usandoTiempoSimulado;
    }

    /**
     * Obtiene el tiempo simulado (puede ser null si no está activo)
     */
    public LocalDateTime getTiempoSimulado() {
        return tiempoSimulado;
    }

    /**
     * Establece manualmente el tiempo simulado (útil para pruebas)
     */
    public void setTiempoSimulado(LocalDateTime tiempo) {
        if (usandoTiempoSimulado) {
            this.tiempoSimulado = tiempo;
            System.out.println("⏰ Tiempo simulado actualizado a: " + tiempo);
        }
    }
}