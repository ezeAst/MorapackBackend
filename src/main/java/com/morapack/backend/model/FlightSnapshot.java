package com.morapack.backend.model;

import com.morapack.algoritmologistica.algorithm.solver.Solucion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private List<String> orderIds = new ArrayList<>();
    // Posición actual
    private double currentLat;
    private double currentLng;
    private Solucion currentSolucion;

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

    public List<String> getOrderIds() {
        return orderIds;
    }

    public void setOrderIds(List<String> orderIds) {
        this.orderIds = orderIds;
    }


}