package com.morapack.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlightSnapshot {

    private String id;
    private String flightCode;

    // Ruta: [[lng_origen, lat_origen], [lng_destino, lat_destino]]
    private double[][] route;
    private String origin;
    private String destination;

    // Posición actual
    private double currentLat;
    private double currentLng;

    // Información temporal
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private long durationSeconds;
    private long elapsedSeconds;

    // Carga
    private int packages;
    private int capacity;

    // Estado: "scheduled", "in_flight", "landed"
    private String status;
    private double progressPercentage;
}