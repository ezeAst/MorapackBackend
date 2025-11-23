package com.morapack.backend.model;

import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Simulation {

    private String id;
    private String type;  // "weekly" o "stress_test"
    private LocalDateTime startTime;
    private LocalDateTime createdAt;

    private SimulationStatus status;

    // Solución generada por GRASP
    private Solucion solucion;

    // Control de tiempo
    private long simulationStartMillis;
    private long elapsedSimulatedSeconds;
    private long pausedDurationMillis;
    private long pauseStartedAtMillis = -1; // -1 indica que no está en pausa actualmente

    // Escala temporal: 1 semana (604800 seg) en 90 min (5400 seg)
    // Factor: 1 segundo real = 112 segundos simulados
    private static final double TIME_SCALE = 112.0;
    private static final long SIMULATION_DURATION_SECONDS = 7 * 24 * 60 * 60; // 1 semana
    private static final long REAL_DURATION_SECONDS = 90 * 60; // 90 minutos

    private List<SimulationEvent> events;
    private SimulationMetrics metrics;

    public Simulation() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = SimulationStatus.STOPPED; // ← Cambiar de RUNNING a STOPPED
        this.events = new ArrayList<>();
        this.metrics = new SimulationMetrics();
        this.simulationStartMillis = 0; // ← Inicializar en 0
        this.elapsedSimulatedSeconds = 0;
        this.pausedDurationMillis = 0;
    }

    /**
     * Calcula el tiempo simulado transcurrido
     */

    public void start() {
        if (status == SimulationStatus.STOPPED) {
            this.simulationStartMillis = System.currentTimeMillis();
            this.status = SimulationStatus.RUNNING;
        }
    }


    public long calculateElapsedSimulatedSeconds() {
        if (status == SimulationStatus.PAUSED) {
            return elapsedSimulatedSeconds;
        }

        long now = System.currentTimeMillis();
        long realElapsedMillis = now - simulationStartMillis - pausedDurationMillis;
        // Evitar valores negativos por desajustes de pausa/reanudar
        if (realElapsedMillis < 0) realElapsedMillis = 0;
        long realElapsedSeconds = realElapsedMillis / 1000;

        return (long) (realElapsedSeconds * TIME_SCALE);
    }

    /**
     * Calcula el progreso (0-100%)
     */
    public double calculateProgress() {
        long elapsed = calculateElapsedSimulatedSeconds();
        return Math.min(100.0, (elapsed * 100.0) / SIMULATION_DURATION_SECONDS);
    }

    /**
     * Verifica si terminó
     */
    public boolean isCompleted() {
        return calculateElapsedSimulatedSeconds() >= SIMULATION_DURATION_SECONDS;
    }

    /**
     * Pausa la simulación
     */
    public void pause() {
        if (status == SimulationStatus.RUNNING) {
            elapsedSimulatedSeconds = calculateElapsedSimulatedSeconds();
            pauseStartedAtMillis = System.currentTimeMillis();
            status = SimulationStatus.PAUSED;
        }
    }

    /**
     * Reanuda la simulación
     */
    public void resume() {
        if (status == SimulationStatus.PAUSED) {
            long currentMillis = System.currentTimeMillis();
            if (pauseStartedAtMillis > 0) {
                pausedDurationMillis += (currentMillis - pauseStartedAtMillis);
            }
            pauseStartedAtMillis = -1;
            status = SimulationStatus.RUNNING;
        }
    }

    /**
     * Detiene la simulación
     */
    public void stop() {
        status = SimulationStatus.STOPPED;
    }

    /**
     * Completa la simulación
     */
    public void complete() {
        status = SimulationStatus.COMPLETED;
    }

    /**
     * Agrega un evento
     */
    public void addEvent(String message, String type) {
        SimulationEvent event = new SimulationEvent(
                message,
                type,
                calculateElapsedSimulatedSeconds()
        );
        events.add(event);
    }

    /**
     * Obtiene eventos recientes
     */
    public List<SimulationEvent> getRecentEvents(int limit) {
        int size = events.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(events.subList(fromIndex, size));
    }
}